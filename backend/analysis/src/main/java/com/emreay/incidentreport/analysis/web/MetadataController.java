package com.emreay.incidentreport.analysis.web;

import com.emreay.incidentreport.analysis.catalog.IncidentCatalog;
import com.emreay.incidentreport.analysis.domain.Province;
import com.emreay.incidentreport.analysis.repository.ProvinceRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serves the catalog and the province list (FR-16).
 *
 * <p>Both live in this module: it owns the catalog it reads texts with, and it owns the relational
 * store the provinces are seeded into. One endpoint rather than two because a client needs them
 * together — every filter and every chart legend is built from this single answer.
 */
@RestController
@RequestMapping("/api/v1/metadata")
public class MetadataController {

    private final IncidentCatalog catalog;
    private final ProvinceRepository provinces;

    public MetadataController(IncidentCatalog catalog, ProvinceRepository provinces) {
        this.catalog = catalog;
        this.provinces = provinces;
    }

    /**
     * Provinces come back ordered by plate code, which is the order Turkish readers expect to see
     * them in and the order they are usually written down.
     */
    @GetMapping
    public MetadataResponse metadata() {
        List<Province> ordered = provinces.findAll(Sort.by(Sort.Direction.ASC, "code"));
        return MetadataResponse.of(catalog, ordered);
    }
}
