package com.emreay.incidentreport.analysis.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The record that answers "how did reading this text go".
 *
 * <p>What matters here is that there is exactly one current answer per report, and that a failure
 * always explains itself.
 */
class AnalysisResultTest {

    private static final String REPORT_ID = "652f1a2b3c4d5e6f70819200";
    private static final Instant AT = Instant.parse("2026-08-09T10:00:00Z");

    @Test
    void aSuccessfulRunRecordsWhatItProducedAndWhatItCouldNotDo() {
        AnalysisResult result = AnalysisResult.analyzed(REPORT_ID, AT, 3, List.of("date was assumed"));

        assertThat(result.getRawReportId()).isEqualTo(REPORT_ID);
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.ANALYZED);
        assertThat(result.getAnalyzedAt()).isEqualTo(AT);
        assertThat(result.getIncidentCount()).isEqualTo(3);
        assertThat(result.getWarnings()).containsExactly("date was assumed");
        assertThat(result.getFailureReason())
                .as("a success has nothing to explain")
                .isNull();
    }

    /** Finding nothing is a legitimate outcome, not a failure. */
    @Test
    void producingNoRecordsIsStillASuccess() {
        AnalysisResult result = AnalysisResult.analyzed(REPORT_ID, AT, 0, List.of());

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.ANALYZED);
        assertThat(result.getIncidentCount()).isZero();
    }

    @Test
    void refusesAnImpossibleCount() {
        assertThatThrownBy(() -> AnalysisResult.analyzed(REPORT_ID, AT, -1, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incidentCount");
    }

    @Test
    void aFailureAlwaysSaysWhy() {
        AnalysisResult result = AnalysisResult.failed(REPORT_ID, AT, "java.lang.IllegalStateException: boom");

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(result.getFailureReason()).contains("IllegalStateException");
        assertThat(result.getIncidentCount()).isZero();

        assertThatThrownBy(() -> AnalysisResult.failed(REPORT_ID, AT, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("failureReason");
    }

    /**
     * Reprocessing asks the same question again rather than a new one. A second row would leave two
     * current answers and force every reader to work out which one is real.
     */
    @Test
    void reprocessingOverwritesTheAnswerInPlace() {
        AnalysisResult result = AnalysisResult.failed(REPORT_ID, AT, "java.lang.IllegalStateException: boom");
        Instant later = AT.plusSeconds(3600);

        result.replaceWith(AnalysisResult.analyzed(REPORT_ID, later, 3, List.of("date was assumed")));

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.ANALYZED);
        assertThat(result.getAnalyzedAt()).isEqualTo(later);
        assertThat(result.getIncidentCount()).isEqualTo(3);
        assertThat(result.getWarnings()).containsExactly("date was assumed");
        assertThat(result.getFailureReason())
                .as("the old failure must not linger next to a successful run")
                .isNull();
    }

    /** Warnings from an earlier run are not part of the current answer either. */
    @Test
    void replacingClearsTheWarningsThatCameBefore() {
        AnalysisResult result = AnalysisResult.analyzed(REPORT_ID, AT, 1, List.of("eski uyarı"));

        result.replaceWith(AnalysisResult.analyzed(REPORT_ID, AT.plusSeconds(60), 2, List.of()));

        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void refusesToBeReplacedByAnotherReportsAnswer() {
        AnalysisResult result = AnalysisResult.analyzed(REPORT_ID, AT, 1, List.of());

        assertThatThrownBy(() -> result.replaceWith(
                AnalysisResult.analyzed("652f1a2b3c4d5e6f70819299", AT, 1, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot replace");
    }

    @Test
    void doesNotKeepTheCallersWarningList() {
        List<String> warnings = new ArrayList<>(List.of("ilk"));

        AnalysisResult result = AnalysisResult.analyzed(REPORT_ID, AT, 1, warnings);
        warnings.add("sonradan");

        assertThat(result.getWarnings()).containsExactly("ilk");
        assertThatThrownBy(() -> result.getWarnings().add("dışarıdan"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void insistsOnTheFieldsThatIdentifyIt() {
        assertThatThrownBy(() -> AnalysisResult.analyzed(null, AT, 0, List.of()))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("rawReportId");
        assertThatThrownBy(() -> AnalysisResult.analyzed(REPORT_ID, null, 0, List.of()))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("analyzedAt");
    }

    /** Like every entity here, it must be safe to log outside a transaction. */
    @Test
    void toStringTouchesNoLazyAssociation() {
        assertThat(AnalysisResult.analyzed(REPORT_ID, AT, 3, List.of("uyarı")).toString())
                .contains(REPORT_ID, "ANALYZED", "3");
    }
}
