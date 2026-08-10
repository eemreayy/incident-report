package com.emreay.incidentreport.analysis.web;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.emreay.incidentreport.analysis.domain.ClassificationStatus;
import com.emreay.incidentreport.analysis.domain.DateSource;
import com.emreay.incidentreport.analysis.domain.Incident;
import com.emreay.incidentreport.analysis.domain.Province;
import com.emreay.incidentreport.analysis.domain.ProvinceScope;

/**
 * One incident record, as the API shows it.
 *
 * <p>Event types carry their key and no label: the catalog is configuration and can grow without a
 * deployment, so its labels have exactly one runtime source, the metadata endpoint (ADR-007).
 * Provinces carry their name as well, because the 81 of them are fixed reference data — a table can
 * be rendered from this response without a second lookup.
 *
 * @param province     the province this record belongs to, or {@code null} when its figures are
 *                     shared between several or the text named none
 * @param sharedAcross the provinces a shared figure covers — never a division of it (ADR-019)
 * @param keywords     what the extractor reacted to, with positions in the raw text, so the source
 *                     can be highlighted without searching it again (FR-17, C-3)
 */
public record IncidentResponse(Long id,
                               String rawReportId,
                               LocalDate occurredOn,
                               DateSource dateSource,
                               String eventType,
                               ClassificationStatus classification,
                               ProvinceScope provinceScope,
                               ProvinceResponse province,
                               List<ProvinceResponse> sharedAcross,
                               List<MetricResponse> metrics,
                               List<KeywordResponse> keywords) {

    public static IncidentResponse of(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getRawReportId(),
                incident.getOccurredOn(),
                incident.getDateSource(),
                incident.getEventType(),
                incident.getClassification(),
                incident.getProvinceScope(),
                ProvinceResponse.of(incident.getProvince()),
                incident.getSharedProvinces().stream()
                        .sorted(Comparator.comparing(Province::getCode))
                        .map(ProvinceResponse::of)
                        .toList(),
                incident.getMetrics().stream()
                        .sorted(Comparator.comparing(metric -> metric.getMetricType()))
                        .map(metric -> new MetricResponse(metric.getMetricType(), metric.getValue()))
                        .toList(),
                incident.getKeywords().stream()
                        .map(KeywordResponse::of)
                        .toList());
    }
}
