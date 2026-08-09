package com.emreay.incidentreport.shared.error;

import java.util.Objects;

/**
 * Thrown when a caller asks for something by id that does not exist.
 *
 * <p>Separate from {@link DomainValidationException} because the two mean different things to a
 * client: one says "your request was wrong", the other says "nothing here". The web layer maps them
 * to 400 and 404 respectively.
 *
 * <p>The message names the resource and the id so the response is useful, and nothing else — an id
 * that was not found is not sensitive, but a database error message would be.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resource;
    private final String id;

    public ResourceNotFoundException(String resource, String id) {
        super(Objects.requireNonNull(resource, "resource") + " " + Objects.requireNonNull(id, "id")
                + " was not found.");
        this.resource = resource;
        this.id = id;
    }

    public String getResource() {
        return resource;
    }

    public String getId() {
        return id;
    }
}
