package com.emreay.incidentreport.shared.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * The stream's payload contract.
 *
 * <p>Two things are worth holding still here. A published event must not change afterwards — it is
 * handed to listeners the publisher knows nothing about — and it must always name the report it
 * came from, because that identifier is what ties a submission to its records in logs, queries and
 * the stream alike (FR-08, NFR-09).
 */
class IncidentRecordsProducedEventTest {

    private static final Instant ANALYZED_AT = Instant.parse("2026-08-10T09:30:00Z");

    @Test
    @DisplayName("an announced record cannot be changed by whoever announced it")
    void theEventIsIndependentOfTheCallersCollections() {
        List<IncidentSignal> incidents = new ArrayList<>(List.of(signal(new HashSet<>(Set.of((short) 16)))));

        IncidentRecordsProducedEvent event = new IncidentRecordsProducedEvent("abc", ANALYZED_AT, incidents);
        incidents.clear();

        assertThat(event.incidents()).hasSize(1);
        assertThat(event.incidents().getFirst().provinceCodes()).containsExactly((short) 16);
    }

    @Test
    @DisplayName("a province set handed in cannot be changed afterwards either")
    void theSignalCopiesItsProvinces() {
        Set<Short> codes = new HashSet<>(Set.of((short) 16, (short) 41));

        IncidentSignal signal = signal(codes);
        codes.clear();

        assertThat(signal.provinceCodes()).containsExactlyInAnyOrder((short) 16, (short) 41);
    }

    @Test
    @DisplayName("a record with no province is announced with an empty set, not a missing one")
    void aRecordWithoutAProvinceCarriesAnEmptySet() {
        assertThat(signal(Set.of()).provinceCodes()).isEmpty();
    }

    @Test
    @DisplayName("an analysis that produced nothing is still announceable")
    void producingNothingIsStillAnEvent() {
        IncidentRecordsProducedEvent event = new IncidentRecordsProducedEvent("abc", ANALYZED_AT, List.of());

        assertThat(event.incidents()).isEmpty();
    }

    @Test
    @DisplayName("an event without a report to point at is not an event")
    void theReportIdIsRequired() {
        assertThatNullPointerException().isThrownBy(
                () -> new IncidentRecordsProducedEvent(null, ANALYZED_AT, List.of()));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new IncidentRecordsProducedEvent("  ", ANALYZED_AT, List.of()));
        assertThatNullPointerException().isThrownBy(
                () -> new IncidentRecordsProducedEvent("abc", null, List.of()));
        assertThatNullPointerException().isThrownBy(
                () -> new IncidentRecordsProducedEvent("abc", ANALYZED_AT, null));
    }

    @Test
    @DisplayName("a signal must name what it signals")
    void theSignalRequiresItsDimensions() {
        assertThatNullPointerException().isThrownBy(
                () -> new IncidentSignal(1L, null, "EPIDEMIC", Set.of()));
        assertThatNullPointerException().isThrownBy(
                () -> new IncidentSignal(1L, LocalDate.of(2020, 4, 20), null, Set.of()));
        assertThatNullPointerException().isThrownBy(
                () -> new IncidentSignal(1L, LocalDate.of(2020, 4, 20), "EPIDEMIC", null));
    }

    private static IncidentSignal signal(Set<Short> provinceCodes) {
        return new IncidentSignal(1L, LocalDate.of(2020, 4, 20), "EPIDEMIC", provinceCodes);
    }
}
