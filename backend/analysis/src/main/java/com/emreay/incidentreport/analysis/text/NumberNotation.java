package com.emreay.incidentreport.analysis.text;

/** How a number was written. Kept because it survives into warnings and is worth seeing in logs. */
public enum NumberNotation {

    /** Written with digits: {@code 15}, {@code 1.500}. */
    DIGITS,

    /** Written in words: {@code on iki}, {@code kırk beş}. */
    WORDS,

    /** Both, as news text often does: {@code 15 bin}, {@code 2 bin 500}. */
    MIXED
}
