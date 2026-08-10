package com.emreay.incidentreport.analysis.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.emreay.incidentreport.analysis.query.IncidentQuery;
import com.emreay.incidentreport.analysis.query.IncidentQueryService;
import com.emreay.incidentreport.shared.api.PageResponse;
import com.emreay.incidentreport.shared.error.ResourceNotFoundException;

/**
 * Reading what was extracted (FR-10, FR-08, FR-17).
 *
 * <p>Every filter is optional and they combine. The one that carries weight is {@code rawReportId}:
 * submission answers with an id and nothing else (ADR-021), so this is the only way to find out what
 * a report produced — and, when it produced nothing, why (C-4, C-5).
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private static final String RESOURCE = "incident";

    private final IncidentQueryService incidents;

    public IncidentController(IncidentQueryService incidents) {
        this.incidents = incidents;
    }

    /**
     * Most recent first: a reader scanning a table wants the latest day at the top, and records
     * from the same day are then ordered by insertion so a page is stable between requests.
     *
     * @param province licence-plate codes. A figure shared between provinces matches if any of them
     *                 is selected, and comes back once however many are (ADR-019)
     */
    @GetMapping
    public IncidentPageResponse find(
            @RequestParam(required = false) List<String> eventType,
            @RequestParam(required = false) List<Short> province,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String rawReportId,
            @PageableDefault(size = 20, sort = {"occurredOn", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {

        IncidentQuery query = IncidentQuery.of(eventType, province, from, to, keyword, rawReportId);
        Page<IncidentResponse> page = incidents.find(query, pageable);

        return IncidentPageResponse.of(
                new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                        page.getTotalElements(), page.getTotalPages()),
                incidents.outcomeFor(query).orElse(null));
    }

    @GetMapping("/{id}")
    public IncidentResponse findOne(@PathVariable long id) {
        return incidents.findOne(id)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, String.valueOf(id)));
    }
}
