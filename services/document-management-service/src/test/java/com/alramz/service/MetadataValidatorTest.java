package com.alramz.service;

import com.alramz.exception.DmsException;
import com.alramz.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/** Brief 8.2 save rules 1-7 and search rules 1/4/5/6/7 (E22-E29). Messages carry ID and name, never the value. */
class MetadataValidatorTest {

    private static final CatalogMetadata CLIENT_ID = md(11329, "CLIENT_ID", MetadataTypeGroup.TEXT, true, false, false);
    private static final CatalogMetadata DOC_TYPE = new CatalogMetadata(11330, "DOCUMENT_TYPE", "LIST", MetadataTypeGroup.LIST,
            true, false, false, null, 7L, List.of(new CatalogMetadata.ListValue(101, "Passport", null),
            new CatalogMetadata.ListValue(102, "Emirates ID", null)));
    private static final CatalogMetadata EXPIRY = md(11331, "EXPIRY_DATE", MetadataTypeGroup.DATE, false, false, false);
    private static final CatalogMetadata REFERENCE = md(11332, "REFERENCE_NO", MetadataTypeGroup.INTEGER, false, false, true);
    private static final CatalogMetadata AMOUNT = md(11333, "AMOUNT", MetadataTypeGroup.DECIMAL, false, false, false);
    private static final CatalogMetadata SIGNED = md(11334, "SIGNED", MetadataTypeGroup.BOOLEAN, false, false, false);
    private static final CatalogMetadata PRODUCTS = new CatalogMetadata(11335, "PRODUCTS", "LIST", MetadataTypeGroup.LIST,
            false, true, false, null, 7L, DOC_TYPE.listValues());
    private static final CatalogMetadata BRANCH = md(11336, "BRANCH", MetadataTypeGroup.SUBLIST, false, false, false);
    private static final CatalogMetadata COUNT = md(11337, "COUNT", MetadataTypeGroup.INTEGER, false, false, false);
    private static final CatalogMetadata OWNER = md(11500, "OWNER", MetadataTypeGroup.UNSUPPORTED, false, false, false);
    private static final CatalogMetadata TAGS = md(11338, "TAGS", MetadataTypeGroup.TEXT, false, true, false);
    private static final CatalogMetadata NO_LIST = new CatalogMetadata(11339, "NO_LIST", "LIST", MetadataTypeGroup.LIST,
            false, false, false, null, 8L, null);

    private static final CatalogCategory CATEGORY = new CatalogCategory(42, "Customer Passport", null, true, null,
            List.of(CLIENT_ID, DOC_TYPE, EXPIRY, REFERENCE, AMOUNT, SIGNED, PRODUCTS, BRANCH, COUNT, OWNER, TAGS, NO_LIST));

    private final MetadataValidator validator = new MetadataValidator();

    // ------------------------------------------------------------------ save: happy path

    @Test
    void save_validMetadata_isNormalisedTyped() {
        Map<String, Object> metadata = base();
        metadata.put("11331", "2027-12-31");
        metadata.put("11333", 12.50);
        metadata.put("11334", "true");
        metadata.put("11335", List.of("Passport", 102));
        metadata.put("11336", "Dubai");
        metadata.put("11337", "42");

        List<ValidatedMetadata> result = validator.validateForSave(CATEGORY, metadata);

        Map<Long, List<Object>> byId = new HashMap<>();
        result.forEach(v -> byId.put(v.definition().id(), v.normalisedValues()));
        assertThat(byId.get(11329L)).containsExactly("FiT-100234");
        assertThat(byId.get(11330L)).containsExactly(new CatalogMetadata.ListValue(101, "Passport", null));
        assertThat(byId.get(11331L)).containsExactly(LocalDate.of(2027, 12, 31));
        assertThat(byId.get(11333L)).containsExactly(new BigDecimal("12.5"));
        assertThat(byId.get(11334L)).containsExactly(Boolean.TRUE);
        assertThat(byId.get(11335L)).extracting("id").containsExactly(101L, 102L);
        assertThat(byId.get(11336L)).containsExactly("Dubai");
        assertThat(byId.get(11337L)).containsExactly(BigInteger.valueOf(42));
        assertThat(result.get(0).toString()).doesNotContain("FiT-100234");
    }

    @Test
    void save_listById_andNumericTypes() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("11329", "X");
        metadata.put("11330", 102);
        metadata.put("11337", 7L);
        metadata.put("11333", "10.125");
        metadata.put("11334", false);

        Map<Long, List<Object>> byId = new HashMap<>();
        validator.validateForSave(CATEGORY, metadata).forEach(v -> byId.put(v.definition().id(), v.normalisedValues()));

        assertThat(byId.get(11330L)).extracting("name").containsExactly("Emirates ID");
        assertThat(byId.get(11337L)).containsExactly(BigInteger.valueOf(7));
        assertThat(byId.get(11333L)).containsExactly(new BigDecimal("10.125"));
        assertThat(byId.get(11334L)).containsExactly(Boolean.FALSE);
    }

    @Test
    void save_largeDoubleAndBigNumbers_areExact() {
        Map<String, Object> metadata = base();
        metadata.put("11333", 1.0E10);
        metadata.put("11337", new BigInteger("123456789012345678901234567890"));

        Map<Long, List<Object>> byId = new HashMap<>();
        validator.validateForSave(CATEGORY, metadata).forEach(v -> byId.put(v.definition().id(), v.normalisedValues()));

        assertThat(((BigDecimal) byId.get(11333L).get(0)).toPlainString()).isEqualTo("10000000000");
        assertThat(byId.get(11337L)).containsExactly(new BigInteger("123456789012345678901234567890"));
    }

    /**
     * The save form's metadata field binds decimals to BigDecimal, so {@code 1e999999999} arrives intact. It must be
     * rejected before toBigIntegerExact()/toPlainString() (which would take minutes and hundreds of MB).
     */
    @Test
    void save_hugeExponentsAndOverlongNumbers_areDms105Immediately() {
        String overlong = "1".repeat(MetadataValidator.MAX_NUMBER_DIGITS + 1);
        List<Map.Entry<String, Object>> cases = List.of(
                Map.entry("11337", new BigDecimal("1E+999999999")),
                Map.entry("11337", new BigDecimal("1E-999999999")),
                Map.entry("11337", new BigDecimal("1E+2147483647")),
                Map.entry("11333", new BigDecimal("1E+999999999")),
                Map.entry("11333", new BigDecimal("1E-999999999")),
                Map.entry("11333", overlong),
                Map.entry("11337", overlong));
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            for (Map.Entry<String, Object> c : cases) {
                Map<String, Object> metadata = base();
                metadata.put(c.getKey(), c.getValue());
                assertError(() -> validator.validateForSave(CATEGORY, metadata), ErrorCode.DMS105, c.getKey());
                assertError(() -> validator.validateForSearch(CATEGORY, new HashMap<>(Map.of(c.getKey(), c.getValue()))),
                        ErrorCode.DMS105, c.getKey());
            }
            assertError(() -> validator.validateForSave(FREE_LIST,
                    new HashMap<>(Map.of("11888", new BigDecimal("1E+999999999")))), ErrorCode.DMS105, "11888 (RequestID)");
        });
    }

    @Test
    void save_numbersAtTheDigitLimit_stayValid() {
        String digits = "9".repeat(MetadataValidator.MAX_NUMBER_DIGITS);
        Map<String, Object> metadata = base();
        metadata.put("11337", digits);
        metadata.put("11333", new BigDecimal("1E+999"));

        Map<Long, List<Object>> byId = new HashMap<>();
        validator.validateForSave(CATEGORY, metadata).forEach(v -> byId.put(v.definition().id(), v.normalisedValues()));

        assertThat(byId.get(11337L)).containsExactly(new BigInteger(digits));
        assertThat(byId.get(11333L)).containsExactly(new BigDecimal("1E+999"));
    }

    @Test
    void save_nullOrEmptyMetadataMap_failsOnlyOnMandatory() {
        assertError(() -> validator.validateForSave(CATEGORY, null), ErrorCode.DMS104, "11329 (CLIENT_ID)");
        CatalogCategory optionalOnly = new CatalogCategory(1, "c", null, null, null, List.of(EXPIRY));
        assertThat(validator.validateForSave(optionalOnly, null)).isEmpty();
    }

    // ------------------------------------------------------------------ save: rule 1

    @Test
    void save_nonNumericKey_isDms103() {
        Map<String, Object> metadata = base();
        metadata.put("CLIENT_ID", "FIT-100234");

        assertError(() -> validator.validateForSave(CATEGORY, metadata), ErrorCode.DMS103, "numeric");
    }

    @Test
    void save_unknownId_isDms103NamingIdAndCategory() {
        Map<String, Object> metadata = base();
        metadata.put("99999", "secret-value");

        assertError(() -> validator.validateForSave(CATEGORY, metadata), ErrorCode.DMS103,
                "Metadata 99999 is not defined on filing category 42");
    }

    @Test
    void firstUnknownId_findsOnlyNumericUnknownKeys() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("abc", "x");
        metadata.put("11329", "x");
        metadata.put("99999", "x");

        assertThat(validator.firstUnknownId(CATEGORY, metadata)).contains(99999L);
        assertThat(validator.firstUnknownId(CATEGORY, base())).isEmpty();
        assertThat(validator.firstUnknownId(CATEGORY, null)).isEmpty();
    }

    // ------------------------------------------------------------------ save: rule 2

    @ParameterizedTest
    @MethodSource("absentValues")
    void save_mandatoryAbsent_isDms104(Object value) {
        Map<String, Object> metadata = base();
        metadata.put("11329", value);

        assertError(() -> validator.validateForSave(CATEGORY, metadata), ErrorCode.DMS104,
                "Mandatory metadata 11329 (CLIENT_ID) is missing");
    }

    static Stream<Object> absentValues() {
        return Stream.of(null, "", List.of());
    }

    @Test
    void save_mandatoryReadOnly_isNotRequired() {
        CatalogMetadata mandatoryAuto = md(5, "AUTO", MetadataTypeGroup.INTEGER, true, false, true);
        CatalogCategory category = new CatalogCategory(1, "c", null, null, null, List.of(mandatoryAuto));

        assertThat(validator.validateForSave(category, Map.of())).isEmpty();
    }

    // ------------------------------------------------------------------ save: rules 3, 4

    @Test
    void save_readOnlySent_isDms105() {
        Map<String, Object> metadata = base();
        metadata.put("11332", 5);

        assertError(() -> validator.validateForSave(CATEGORY, metadata), ErrorCode.DMS105,
                "Metadata 11332 (REFERENCE_NO) is read-only and must not be sent");
    }

    @Test
    void save_arrayForNonMultiple_isDms105() {
        Map<String, Object> metadata = base();
        metadata.put("11329", List.of("A", "B"));

        assertError(() -> validator.validateForSave(CATEGORY, metadata), ErrorCode.DMS105,
                "Multiple values are not supported for metadata 11329 (CLIENT_ID)");
    }

    @Test
    void save_arrayWithNullOrNestedItem_isDms105() {
        Map<String, Object> metadata = base();
        metadata.put("11338", Arrays.asList("A", null));
        assertError(() -> validator.validateForSave(CATEGORY, metadata), ErrorCode.DMS105, "11338 (TAGS)");

        metadata.put("11338", List.of(List.of("A")));
        assertError(() -> validator.validateForSave(CATEGORY, metadata), ErrorCode.DMS105, "11338 (TAGS)");
    }

    // ------------------------------------------------------------------ save: rule 5

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("invalidValues")
    void save_valueNotMatchingItsType_isDms105WithoutTheValue(String key, Object value, String label) {
        Map<String, Object> metadata = base();
        metadata.put(key, value);

        assertError(() -> validator.validateForSave(CATEGORY, metadata), ErrorCode.DMS105,
                "Invalid value for metadata " + label);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> invalidValues() {
        return Stream.of(
                arguments("11336", "   ", "11336 (BRANCH)"),
                arguments("11336", 5, "11336 (BRANCH)"),
                arguments("11336", "x".repeat(4001), "11336 (BRANCH)"),
                arguments("11337", "12.5", "11337 (COUNT)"),
                arguments("11337", "abc", "11337 (COUNT)"),
                arguments("11337", 1.5, "11337 (COUNT)"),
                arguments("11337", true, "11337 (COUNT)"),
                arguments("11337", "1e3", "11337 (COUNT)"),
                arguments("11333", "12,50", "11333 (AMOUNT)"),
                arguments("11333", " ", "11333 (AMOUNT)"),
                arguments("11333", Double.NaN, "11333 (AMOUNT)"),
                arguments("11333", Map.of("a", 1), "11333 (AMOUNT)"),
                arguments("11331", "31/12/2027", "11331 (EXPIRY_DATE)"),
                arguments("11331", "2027-02-30", "11331 (EXPIRY_DATE)"),
                arguments("11331", 20271231, "11331 (EXPIRY_DATE)"),
                arguments("11334", "TRUE", "11334 (SIGNED)"),
                arguments("11334", 1, "11334 (SIGNED)"),
                arguments("11330", "passport", "11330 (DOCUMENT_TYPE)"),
                arguments("11330", 999, "11330 (DOCUMENT_TYPE)"),
                arguments("11330", true, "11330 (DOCUMENT_TYPE)"),
                arguments("11339", "anything", "11339 (NO_LIST)"));
    }

    @Test
    void save_mixedCaseText_isKeptByteForByte() {
        Map<String, Object> metadata = base();
        metadata.put("11336", "  DuBaI Main  ");

        Map<Long, List<Object>> byId = new HashMap<>();
        validator.validateForSave(CATEGORY, metadata).forEach(v -> byId.put(v.definition().id(), v.normalisedValues()));

        assertThat(byId.get(11336L)).containsExactly("  DuBaI Main  ");
    }

    // ------------------------------------------------------------------ save: rule 7

    @Test
    void save_unsupportedTypeSent_isDms110() {
        Map<String, Object> metadata = base();
        metadata.put("11500", "someone");

        assertError(() -> validator.validateForSave(CATEGORY, metadata), ErrorCode.DMS110,
                "Metadata 11500 (OWNER) has a type not supported by this service");
    }

    @Test
    void save_mandatoryUnsupportedOnCategory_isDms110() {
        CatalogMetadata mandatoryFile = md(11501, "SCAN", MetadataTypeGroup.UNSUPPORTED, true, false, false);
        CatalogCategory category = new CatalogCategory(9, "c", null, null, null, List.of(CLIENT_ID, mandatoryFile));

        assertError(() -> validator.validateForSave(category, Map.of("11329", "FiT-100234")), ErrorCode.DMS110, "11501 (SCAN)");
    }

    // ------------------------------------------------------------------ search

    @Test
    void search_noMandatoryCheck_andReadOnlyAllowed() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("11332", 12);

        List<ValidatedMetadata> result = validator.validateForSearch(CATEGORY, metadata);

        assertThat(result).singleElement().satisfies(v -> assertThat(v.normalisedValues()).containsExactly(BigInteger.valueOf(12)));
        assertThat(validator.validateForSearch(CATEGORY, null)).isEmpty();
    }

    @Test
    void search_arraysAllowedForTextListAndSublist() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("11329", List.of("A", "B"));
        metadata.put("11330", List.of("Passport", "Emirates ID"));
        metadata.put("11336", List.of("Dubai", "Abu Dhabi"));
        metadata.put("11331", null);

        assertThat(validator.validateForSearch(CATEGORY, metadata)).hasSize(3);
    }

    @ParameterizedTest
    @MethodSource("searchArrayRejected")
    void search_arraysRejectedForOtherTypes(String key, Object value, String label) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(key, value);

        assertError(() -> validator.validateForSearch(CATEGORY, metadata), ErrorCode.DMS105,
                "Multiple values are not supported for metadata " + label);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> searchArrayRejected() {
        return Stream.of(
                arguments("11331", List.of("2027-12-31"), "11331 (EXPIRY_DATE)"),
                arguments("11337", List.of(1, 2), "11337 (COUNT)"),
                arguments("11333", List.of(1.5), "11333 (AMOUNT)"),
                arguments("11334", List.of(true), "11334 (SIGNED)"));
    }

    @Test
    void search_unknownUnsupportedAndInvalid() {
        assertError(() -> validator.validateForSearch(CATEGORY, Map.of("99999", "x")), ErrorCode.DMS103, "99999");
        assertError(() -> validator.validateForSearch(CATEGORY, Map.of("11500", "x")), ErrorCode.DMS110, "11500 (OWNER)");
        assertError(() -> validator.validateForSearch(CATEGORY, Map.of("11331", "tomorrow")), ErrorCode.DMS105,
                "11331 (EXPIRY_DATE)");
    }

    @Test
    void typeGroups_fromOpenBeeNames() {
        assertThat(Stream.of("VARCHAR", "TEXT", "EMAIL", "ADDRESS").map(MetadataTypeGroup::fromOpenBeeType)).containsOnly(MetadataTypeGroup.TEXT);
        assertThat(Stream.of("INT", "INTEGER", "AUTOINCREMENT").map(MetadataTypeGroup::fromOpenBeeType)).containsOnly(MetadataTypeGroup.INTEGER);
        assertThat(MetadataTypeGroup.fromOpenBeeType("decimal")).isEqualTo(MetadataTypeGroup.DECIMAL);
        assertThat(MetadataTypeGroup.fromOpenBeeType("DATE")).isEqualTo(MetadataTypeGroup.DATE);
        assertThat(MetadataTypeGroup.fromOpenBeeType("BOOLEAN")).isEqualTo(MetadataTypeGroup.BOOLEAN);
        assertThat(Stream.of("LIST", "MULTIPLE_CHOICE").map(MetadataTypeGroup::fromOpenBeeType)).containsOnly(MetadataTypeGroup.LIST);
        assertThat(MetadataTypeGroup.fromOpenBeeType("SUBLIST")).isEqualTo(MetadataTypeGroup.SUBLIST);
        assertThat(Stream.of("USER_GROUP", "FILE").map(MetadataTypeGroup::fromOpenBeeType)).containsOnly(MetadataTypeGroup.UNSUPPORTED);
        assertThat(MetadataTypeGroup.fromOpenBeeType("GEOPOINT")).isEqualTo(MetadataTypeGroup.TEXT);
        assertThat(MetadataTypeGroup.fromOpenBeeType(null)).isEqualTo(MetadataTypeGroup.TEXT);
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> base() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("11329", "FiT-100234");
        metadata.put("11330", "Passport");
        return metadata;
    }

    // ------------------------------------------------------------------ allowNewValue (Open Bee addNewValue)

    private static final CatalogMetadata REQUEST_ID = new CatalogMetadata(11888, "RequestID", "LIST", MetadataTypeGroup.LIST,
            false, true, false, null, 11887L, null, true);
    private static final CatalogCategory FREE_LIST = new CatalogCategory(11373, "Share Transfer In", null, true, null,
            List.of(REQUEST_ID));

    @Test
    void allowNewValue_acceptsAnyTextOrWholeNumber_asEntered() {
        List<ValidatedMetadata> saved = validator.validateForSave(FREE_LIST,
                new HashMap<>(Map.of("11888", List.of("NEW-VALUE", "1001", 102))));
        assertThat(saved).singleElement().satisfies(v -> assertThat(v.normalisedValues())
                .containsExactly("NEW-VALUE", "1001", "102"));

        List<ValidatedMetadata> searched = validator.validateForSearch(FREE_LIST, new HashMap<>(Map.of("11888", "NEW-VALUE")));
        assertThat(searched).singleElement().satisfies(v -> assertThat(v.normalisedValues()).containsExactly("NEW-VALUE"));
    }

    @Test
    void allowNewValue_stillRejectsBlankOrNonScalarValues() {
        assertError(() -> validator.validateForSave(FREE_LIST, new HashMap<>(Map.of("11888", List.of(" ")))),
                ErrorCode.DMS105, "11888 (RequestID)");
        assertError(() -> validator.validateForSave(FREE_LIST, new HashMap<>(Map.of("11888", List.of(true)))),
                ErrorCode.DMS105, "11888 (RequestID)");
        assertError(() -> validator.validateForSave(FREE_LIST, new HashMap<>(Map.of("11888", List.of(1.5)))),
                ErrorCode.DMS105, "11888 (RequestID)");
    }

    /** A new free-entry value is stored in Open Bee's shared list for good: control characters are refused. */
    @Test
    void allowNewValue_rejectsControlCharacters() {
        for (String value : List.of("line\nbreak", "tab\there", "nul\u0000", "del\u007f")) {
            assertError(() -> validator.validateForSave(FREE_LIST, new HashMap<>(Map.of("11888", List.of(value)))),
                    ErrorCode.DMS105, "11888 (RequestID)");
        }
        assertThat(validator.validateForSave(FREE_LIST, new HashMap<>(Map.of("11888", List.of("عميل-1 / A&B")))))
                .singleElement().satisfies(v -> assertThat(v.normalisedValues()).containsExactly("عميل-1 / A&B"));
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code, String messagePart) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DmsException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(code);
            assertThat(e.getMessage()).contains(messagePart)
                    .doesNotContain("FiT-100234").doesNotContain("secret-value").doesNotContain("someone");
        });
    }

    private static CatalogMetadata md(long id, String name, MetadataTypeGroup group, boolean mandatory, boolean multiple,
                                      boolean readOnly) {
        return new CatalogMetadata(id, name, group.name(), group, mandatory, multiple, readOnly, null, null, null);
    }
}
