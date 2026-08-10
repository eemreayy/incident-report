package com.emreay.incidentreport.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * The description that wraps the generated API documentation (NFR-07).
 *
 * <p>Only the covering note is written here. Every path, parameter and schema is derived from the
 * controllers and DTOs, because a hand-maintained document describes the API as someone remembered
 * it rather than as it is.
 *
 * <p>It lives in {@code app} for the same reason the exception handler does: this is the only
 * module that sees every controller.
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI incidentReportApi(@Value("${spring.application.name}") String name) {
        return new OpenAPI().info(new Info()
                .title("Incident Report API")
                .version("v1")
                .description("""
                        Free-form Turkish text in, structured incidents out.

                        Two things are worth knowing before reading the paths.

                        Submission answers with a receipt — the raw report's id and the time it was \
                        stored — and never with a result. Storing the text and analysing it are \
                        separate concerns, so a caller reads what was extracted through \
                        `GET /incidents?rawReportId=...`, which also reports whether analysis \
                        succeeded. That is the only place the outcome appears, because when \
                        analysis fails there are no records to hang it on.

                        A figure the text gives for several provinces at once — "her iki ilde \
                        toplam 10 kişi" — belongs to none of them individually. It is stored as its \
                        own record with a `SHARED` scope: never divided between them, never \
                        dropped, and counted once however many of them are filtered for.

                        Errors are RFC 7807 problem documents. Clients should branch on `code`; \
                        `detail` is prose and may change.
                        """)
                .summary("API of " + name));
    }
}
