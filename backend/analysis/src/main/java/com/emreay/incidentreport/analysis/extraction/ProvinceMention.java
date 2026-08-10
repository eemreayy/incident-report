package com.emreay.incidentreport.analysis.extraction;

import java.util.Objects;

/**
 * A province named in the text, and where it was named.
 *
 * <p>Position is what makes a mention usable rather than merely present. A text can name several
 * provinces and give each its own figures — "Bursa'da 8, Kocaeli'nde 6" — and the only thing that
 * says which number belongs to which province is how close they are (TC-3).
 *
 * @param code  the licence-plate code, which is what a record is stored against
 * @param name  the province's canonical name, spelled as the reference data spells it
 * @param start where the mention begins in the normalized text, inclusive
 * @param end   index just past the mention, which includes any suffix that was attached to it
 */
public record ProvinceMention(short code, String name, int start, int end) {

    public ProvinceMention {
        Objects.requireNonNull(name, "name");
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("invalid province range [" + start + ", " + end + ")");
        }
    }
}
