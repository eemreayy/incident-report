package com.emreay.incidentreport.shared.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The answer travelling back across the module boundary. Like the outbound event, its shape is a
 * contract: the side that stored the text has no other way to learn how the analysis went.
 */
class RawReportAnalyzedEventTest {

    @Test
    void carriesTheOutcomeBackToTheReport() {
        RawReportAnalyzedEvent event = new RawReportAnalyzedEvent(
                "652f1a2b3c4d5e6f70819200", 3, List.of("event type not recognised"));

        assertThat(event.rawReportId()).isEqualTo("652f1a2b3c4d5e6f70819200");
        assertThat(event.incidentCount()).isEqualTo(3);
        assertThat(event.warnings()).containsExactly("event type not recognised");
    }

    /** Finding nothing is a legitimate outcome and must be reportable. */
    @Test
    void zeroRecordsIsAValidOutcome() {
        assertThat(new RawReportAnalyzedEvent("id", 0, List.of()).incidentCount()).isZero();
    }

    @Test
    void refusesAnImpossibleCount() {
        assertThatThrownBy(() -> new RawReportAnalyzedEvent("id", -1, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incidentCount");
    }

    @Test
    void refusesToAnswerAboutNoReport() {
        assertThatThrownBy(() -> new RawReportAnalyzedEvent("  ", 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rawReportId");
        assertThatThrownBy(() -> new RawReportAnalyzedEvent(null, 0, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void doesNotKeepThePublishersList() {
        List<String> warnings = new ArrayList<>(List.of("ilk"));

        RawReportAnalyzedEvent event = new RawReportAnalyzedEvent("id", 1, warnings);
        warnings.add("sonradan");

        assertThat(event.warnings()).containsExactly("ilk");
    }
}
