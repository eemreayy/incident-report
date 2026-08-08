package com.emreay.incidentreport.shared.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The code is the half of this contract callers are allowed to depend on; the message is free to be
 * reworded. Both must actually be present, or the web layer has nothing to map.
 */
class DomainValidationExceptionTest {

    @Test
    void carriesAStableCodeAlongsideTheMessage() {
        DomainValidationException exception =
                new DomainValidationException("report.text.blank", "Incident report text must not be empty.");

        assertThat(exception.getCode()).isEqualTo("report.text.blank");
        assertThat(exception.getMessage()).isEqualTo("Incident report text must not be empty.");
    }

    @Test
    void refusesToBeBuiltWithoutEither() {
        assertThatThrownBy(() -> new DomainValidationException(null, "message"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("code");
        assertThatThrownBy(() -> new DomainValidationException("code", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("message");
    }
}
