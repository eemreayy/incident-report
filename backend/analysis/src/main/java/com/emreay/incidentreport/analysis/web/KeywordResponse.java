package com.emreay.incidentreport.analysis.web;

import com.emreay.incidentreport.analysis.domain.IncidentKeyword;
import com.emreay.incidentreport.analysis.domain.KeywordRole;

/**
 * A word the extractor reacted to, and where it is in the submitted text.
 *
 * <p>The offsets are the point (C-3). The interface highlights the raw text, and it cannot find
 * these words by searching for them: Turkish suffixes and apostrophes mean the stored keyword and
 * the text rarely match character for character, so a client-side search would mark the wrong
 * place or nothing at all (TC-18).
 */
public record KeywordResponse(String keyword, KeywordRole role, Integer charStart, Integer charEnd) {

    static KeywordResponse of(IncidentKeyword keyword) {
        return new KeywordResponse(keyword.getKeyword(), keyword.getRole(),
                keyword.getCharStart(), keyword.getCharEnd());
    }
}
