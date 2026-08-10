package com.emreay.incidentreport.analysis.web;

import com.emreay.incidentreport.analysis.domain.Province;

/** A province, by the code it is stored against and the name a reader recognises. */
public record ProvinceResponse(short code, String name) {

    static ProvinceResponse of(Province province) {
        return province == null ? null : new ProvinceResponse(province.getCode(), province.getName());
    }
}
