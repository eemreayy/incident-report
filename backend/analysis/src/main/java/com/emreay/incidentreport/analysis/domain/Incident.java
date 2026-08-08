package com.emreay.incidentreport.analysis.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A structured incident record derived from a raw report.
 *
 * <p>The grain is <strong>(raw report, date, province, event type)</strong> — ADR-019. The system
 * exists to show incidents over time and by geographic region, so date and province are part of the
 * record's identity rather than attributes tucked inside it. One raw text therefore produces as
 * many records as it contains distinct combinations: the third sample text in the source document
 * yields three, one for Bursa, one for Kocaeli, and one for the injured figure the text gives for
 * both provinces together.
 *
 * <p>The three factory methods exist so that the province/scope invariant cannot be broken from
 * Java, mirroring the {@code incident_province_matches_scope} constraint in the schema. There is no
 * constructor that lets you attach a single province to a {@code SHARED} record — which would
 * quietly turn a figure belonging to several provinces into one province's own number.
 */
@Entity
@Table(name = "incident")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * MongoDB ObjectId of the source text. Half of the two-way traceability FR-08 requires; the
     * other half is a query on this column. Not a foreign key — the two stores are separate.
     */
    @Column(name = "raw_report_id", nullable = false, length = 24)
    private String rawReportId;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_source", nullable = false, length = 16)
    private DateSource dateSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "province_scope", nullable = false, length = 16)
    private ProvinceScope provinceScope;

    /** Set only when the scope is {@link ProvinceScope#SINGLE}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_code")
    private Province province;

    /**
     * Provinces a {@link ProvinceScope#SHARED} record spans.
     *
     * <p>This records <em>coverage, not allocation</em>. The figure is not divided among these
     * provinces and is never added to any one of their totals; the link exists so a province
     * filtered view can surface "there is also a figure shared with Kocaeli" instead of dropping it
     * silently. When several provinces are selected at once, such a record must be counted once.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "incident_shared_province",
            joinColumns = @JoinColumn(name = "incident_id"),
            inverseJoinColumns = @JoinColumn(name = "province_code"))
    private Set<Province> sharedProvinces = new LinkedHashSet<>();

    /** Catalog key, not a database enum — the catalog grows without a migration (ADR-007). */
    @Column(name = "event_type", nullable = false, length = 48)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false, length = 16)
    private ClassificationStatus classification;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IncidentMetric> metrics = new ArrayList<>();

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IncidentKeyword> keywords = new ArrayList<>();

    protected Incident() {
        // for JPA
    }

    private Incident(String rawReportId, LocalDate occurredOn, DateSource dateSource,
                     ProvinceScope provinceScope, String eventType, ClassificationStatus classification) {
        this.rawReportId = Objects.requireNonNull(rawReportId, "rawReportId");
        this.occurredOn = Objects.requireNonNull(occurredOn, "occurredOn");
        this.dateSource = Objects.requireNonNull(dateSource, "dateSource");
        this.provinceScope = provinceScope;
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.classification = Objects.requireNonNull(classification, "classification");
    }

    /** Numbers that belong to one named province. */
    public static Incident forProvince(String rawReportId, LocalDate occurredOn, DateSource dateSource,
                                       Province province, String eventType,
                                       ClassificationStatus classification) {
        Incident incident = new Incident(rawReportId, occurredOn, dateSource,
                ProvinceScope.SINGLE, eventType, classification);
        incident.province = Objects.requireNonNull(province, "province");
        return incident;
    }

    /**
     * Numbers the text gives across several provinces at once, without splitting them.
     *
     * @param coveredProvinces the provinces the figure spans; at least two, since a figure shared
     *                         with nobody is simply that province's own figure
     */
    public static Incident sharedAcross(String rawReportId, LocalDate occurredOn, DateSource dateSource,
                                        Collection<Province> coveredProvinces, String eventType,
                                        ClassificationStatus classification) {
        Objects.requireNonNull(coveredProvinces, "coveredProvinces");
        if (coveredProvinces.size() < 2) {
            throw new IllegalArgumentException(
                    "a SHARED incident must span at least two provinces, got " + coveredProvinces.size());
        }
        Incident incident = new Incident(rawReportId, occurredOn, dateSource,
                ProvinceScope.SHARED, eventType, classification);
        incident.sharedProvinces.addAll(coveredProvinces);
        return incident;
    }

    /** Numbers with no province anywhere in the text. */
    public static Incident withoutProvince(String rawReportId, LocalDate occurredOn, DateSource dateSource,
                                           String eventType, ClassificationStatus classification) {
        return new Incident(rawReportId, occurredOn, dateSource,
                ProvinceScope.UNKNOWN, eventType, classification);
    }

    public Incident addMetric(String metricType, int value) {
        metrics.add(new IncidentMetric(this, metricType, value));
        return this;
    }

    public Incident addKeyword(String keyword, KeywordRole role, Integer charStart, Integer charEnd) {
        keywords.add(new IncidentKeyword(this, keyword, role, charStart, charEnd));
        return this;
    }

    @PrePersist
    void assignCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getRawReportId() {
        return rawReportId;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public DateSource getDateSource() {
        return dateSource;
    }

    public ProvinceScope getProvinceScope() {
        return provinceScope;
    }

    /** The province these numbers belong to, or {@code null} unless the scope is {@code SINGLE}. */
    public Province getProvince() {
        return province;
    }

    public Set<Province> getSharedProvinces() {
        return Set.copyOf(sharedProvinces);
    }

    public String getEventType() {
        return eventType;
    }

    public ClassificationStatus getClassification() {
        return classification;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<IncidentMetric> getMetrics() {
        return List.copyOf(metrics);
    }

    public List<IncidentKeyword> getKeywords() {
        return List.copyOf(keywords);
    }

    /**
     * Deliberately touches no lazy association. A {@code toString} that dereferences
     * {@code province} or {@code sharedProvinces} works fine inside a transaction and throws
     * {@code LazyInitializationException} the first time something logs the entity outside one —
     * and it would do so from the logging call, which is the worst possible place to debug.
     */
    @Override
    public String toString() {
        return "Incident[id=" + id + ", " + eventType + ", " + occurredOn + ", " + provinceScope + "]";
    }
}
