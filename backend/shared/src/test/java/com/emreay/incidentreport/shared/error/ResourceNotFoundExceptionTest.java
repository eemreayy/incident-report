package com.emreay.incidentreport.shared.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The message is what a caller reads in the 404 body, so its shape is part of the contract rather
 * than a debugging aid.
 */
class ResourceNotFoundExceptionTest {

    @Test
    void namesWhatWasNotFound() {
        ResourceNotFoundException exception =
                new ResourceNotFoundException("Incident report", "652f1a2b3c4d5e6f70819200");

        assertThat(exception.getMessage())
                .isEqualTo("Incident report 652f1a2b3c4d5e6f70819200 was not found.");
        assertThat(exception.getResource()).isEqualTo("Incident report");
        assertThat(exception.getId()).isEqualTo("652f1a2b3c4d5e6f70819200");
    }

    @Test
    void refusesToBeBuiltWithoutEither() {
        assertThatThrownBy(() -> new ResourceNotFoundException(null, "id"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resource");
        assertThatThrownBy(() -> new ResourceNotFoundException("Incident report", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");
    }
}
