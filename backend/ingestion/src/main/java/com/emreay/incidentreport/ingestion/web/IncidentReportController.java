package com.emreay.incidentreport.ingestion.web;

import com.emreay.incidentreport.ingestion.domain.RawIncidentReport;
import com.emreay.incidentreport.ingestion.service.IngestionService;
import com.emreay.incidentreport.shared.api.PageResponse;
import com.emreay.incidentreport.shared.error.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * The write side of the API: submitting raw reports and reading them back.
 *
 * <p>There is no update and no delete endpoint, and there will not be one. The raw text is an audit
 * log — a record that can be edited cannot explain what was derived from it (FR-02, ADR-005).
 *
 * <p>Structured records extracted from these reports are served elsewhere, by the analysis module.
 * The two are linked by this id in both directions (FR-08), which is why the response always
 * carries it.
 */
@RestController
@RequestMapping("/api/v1/incident-reports")
public class IncidentReportController {

    private static final String RESOURCE = "Incident report";

    private final IngestionService ingestionService;

    public IncidentReportController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * Accepts a report and stores it.
     *
     * <p>Answers <strong>201 Created</strong> with a receipt — the report's id and when it arrived
     * — and nothing about what was extracted from it (ADR-021). What the analysis found is read
     * through {@code GET /incidents?rawReportId=...}, because that data belongs to the module that
     * produces it.
     *
     * <p>201 rather than 202: the resource this endpoint creates is the raw report, and by the time
     * the response is written it exists. Whether analysis has finished is a separate question with
     * its own answer, which is precisely why it is not answered here.
     *
     * <p>A submission succeeds even when analysis does not. The text was stored, which is the
     * guarantee that matters; the failure is recorded where the analysis outcome lives.
     */
    @PostMapping
    public ResponseEntity<IncidentReportReceipt> submit(@RequestBody SubmitIncidentReportRequest request) {
        RawIncidentReport stored = ingestionService.submit(request.text());
        return ResponseEntity
                .created(URI.create("/api/v1/incident-reports/" + stored.id()))
                .body(IncidentReportReceipt.from(stored));
    }

    @GetMapping("/{id}")
    public IncidentReportResponse findOne(@PathVariable String id) {
        return ingestionService.findById(id)
                .map(IncidentReportResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    /** Newest first, because a reader looking at a log wants the latest submissions. */
    @GetMapping
    public PageResponse<IncidentReportResponse> findAll(
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<IncidentReportResponse> page = ingestionService.findAll(pageable)
                .map(IncidentReportResponse::from);

        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
