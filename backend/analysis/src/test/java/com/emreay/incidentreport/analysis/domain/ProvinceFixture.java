package com.emreay.incidentreport.analysis.domain;

import org.springframework.test.util.ReflectionTestUtils;

/**
 * Builds a {@link Province} for tests.
 *
 * <p>{@code Province} has no public constructor because its rows come from a Flyway migration and
 * nothing in the application ever creates one. Tests still need instances, and the obvious answer —
 * mocking it — turned out to be a trap: building a mock inside a {@code when(...)} call leaves
 * Mockito mid-stubbing, and the failure it produces points at the wrong line. That happened three
 * times before this existed.
 *
 * <p>A real object with its fields set is simpler to reason about anyway: equality, {@code hashCode}
 * and {@code toString} behave the way production code will see them, which matters because
 * {@code Incident} keeps provinces in a {@code Set}.
 */
public final class ProvinceFixture {

    private ProvinceFixture() {
    }

    public static Province province(int code, String name) {
        Province province = new Province();
        ReflectionTestUtils.setField(province, "code", (short) code);
        ReflectionTestUtils.setField(province, "name", name);
        return province;
    }
}
