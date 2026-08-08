package com.emreay.incidentreport.shared.error;

import java.util.Objects;

/**
 * Thrown when a request is rejected because of the domain's own rules rather than a technical
 * failure — text that is empty, or longer than the system accepts.
 *
 * <p>Lives in the shared kernel because it is part of the error contract every module answers with:
 * one web layer maps it to an RFC 7807 {@code problem+json} response, and it needs a single type to
 * key on. It stays free of any web dependency, so the shared module keeps depending on nothing but
 * the Spring context.
 *
 * <p>{@code code} is the stable, machine-readable half of the contract — clients and the frontend
 * branch on it, while {@code message} is free to be reworded.
 */
public class DomainValidationException extends RuntimeException {

    private final String code;

    /**
     * @param code    stable identifier such as {@code report.text.blank}; safe for callers to
     *                depend on
     * @param message human-readable explanation; never contains internal detail, since it is
     *                returned to the caller
     */
    public DomainValidationException(String code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public String getCode() {
        return code;
    }
}
