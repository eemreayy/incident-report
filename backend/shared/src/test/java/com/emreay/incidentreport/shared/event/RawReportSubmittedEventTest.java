package com.emreay.incidentreport.shared.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The event is the only thing that crosses the module boundary, so its shape is a contract rather
 * than an implementation detail — a listener has no other source for the text it must analyse.
 * These checks make a malformed event fail where it is created, not three modules later.
 */
class RawReportSubmittedEventTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-09T09:30:00Z");

    @Test
    void carriesTheTextAndTheReferenceDate() {
        RawReportSubmittedEvent event =
                new RawReportSubmittedEvent("652f1a2b3c4d5e6f70819200", "Ankara'da 15 vaka", SUBMITTED_AT);

        assertThat(event.rawReportId()).isEqualTo("652f1a2b3c4d5e6f70819200");
        assertThat(event.rawText()).isEqualTo("Ankara'da 15 vaka");
        assertThat(event.submittedAt()).isEqualTo(SUBMITTED_AT);
    }

    /** An event with no text would leave the listener nothing to do and no way to say so. */
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n"})
    void refusesToCarryEmptyText(String rawText) {
        assertThatThrownBy(() -> new RawReportSubmittedEvent("652f1a2b3c4d5e6f70819200", rawText, SUBMITTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rawText");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void refusesToCarryAnEmptyReportId(String rawReportId) {
        assertThatThrownBy(() -> new RawReportSubmittedEvent(rawReportId, "metin", SUBMITTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rawReportId");
    }

    @Test
    void refusesNulls() {
        assertThatThrownBy(() -> new RawReportSubmittedEvent(null, "metin", SUBMITTED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RawReportSubmittedEvent("id", null, SUBMITTED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RawReportSubmittedEvent("id", "metin", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("submittedAt");
    }
}
