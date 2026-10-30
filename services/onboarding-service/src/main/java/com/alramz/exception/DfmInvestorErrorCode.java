package com.alramz.exception;

import java.util.Collection;
import java.util.List;

/**
 * Error codes of {@code POST /api/v1/dfm/investors}. Unlike utility-service's ErrorCode.forField, field matching uses
 * the FULL property path, so {@code crs.countries[0].country} is not mistaken for {@code address.country}.
 */
public enum DfmInvestorErrorCode {
    DFN001("address.country"),
    DFN002("address.city"),
    DFN003(null),
    DFN004("mobile"),
    DFN005("address.line"),
    DFN006(null),
    DFN007(null),
    DFN010(null),
    DFN011(null),
    DFN012(null),
    DFN013(null),
    DFN014(null);

    /** When several fields fail at once, the first of these that matches wins; otherwise DFN003. */
    private static final List<DfmInvestorErrorCode> FIELD_PRECEDENCE = List.of(DFN001, DFN002, DFN004, DFN005);

    private final String field;

    DfmInvestorErrorCode(String field) {
        this.field = field;
    }

    public String code() {
        return name();
    }

    public static DfmInvestorErrorCode forFields(Collection<String> fieldPaths) {
        return FIELD_PRECEDENCE.stream()
                .filter(code -> fieldPaths.contains(code.field))
                .findFirst()
                .orElse(DFN003);
    }
}
