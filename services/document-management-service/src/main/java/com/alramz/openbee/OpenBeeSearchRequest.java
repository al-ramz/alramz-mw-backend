package com.alramz.openbee;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * {@code POST /advancedSearch/ws/advanced-search} body (brief Appendix C.4). Null clause fields are omitted, so
 * each clause type emits only its own fields.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenBeeSearchRequest(List<Filter> filters, PaginationSpec pagination, List<Sort> sort) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Filter(String field, Long idMetadata, Clause filter) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Clause(String type, Object value, List<Object> values, String comparator, String start, String end,
                         Boolean includeTrue, Boolean includeFalse, Boolean includeEmpty) {

        public static Clause equalsTo(Object value) {
            return new Clause("EQUALS", value, null, null, null, null, null, null, null);
        }

        public static Clause in(List<Object> values) {
            return new Clause("IN", null, values, null, null, null, null, null, null);
        }

        public static Clause comparison(String comparator, Object value) {
            return new Clause("COMPARISON", value, null, comparator, null, null, null, null, null);
        }

        public static Clause dateRange(String start, String end) {
            return new Clause("DATE_RANGE", null, null, null, start, end, null, null, null);
        }

        public static Clause bool(boolean value) {
            return new Clause("BOOLEAN", null, null, null, null, null, value, !value, false);
        }
    }

    public record PaginationSpec(int itemsPerPage, int page) {
    }

    public record Sort(String field, String order) {
    }
}
