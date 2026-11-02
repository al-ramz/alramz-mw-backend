package com.alramz.openbee;

import com.alramz.config.DmsProperties;
import com.alramz.service.CatalogMetadata;
import com.alramz.service.MetadataTypeGroup;
import com.alramz.service.ValidatedMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Brief 8.4 / Appendix C.4: exact JSON per type, always the category and TYPE filters, pagination and sort. */
class SearchFilterBuilderTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private DmsProperties properties;
    private SearchFilterBuilder builder;

    @BeforeEach
    void setUp() {
        properties = new DmsProperties();
        builder = new SearchFilterBuilder(new MetadataEncoder(properties));
    }

    @Test
    void appendixC4_textAndDate() {
        OpenBeeSearchRequest request = builder.build(42, List.of(
                vm(11329, MetadataTypeGroup.TEXT, "FIT-100234"),
                vm(11331, MetadataTypeGroup.DATE, LocalDate.of(2027, 12, 31))), 1, 25);

        assertJson(request, """
                {
                  "filters": [
                    { "field": "FILING_CATEGORY", "filter": { "type": "EQUALS", "value": 42 } },
                    { "field": "TYPE", "filter": { "type": "IN", "values": ["PDF", "OFFICE", "IMAGE", "AUDIO", "OTHERS"] } },
                    { "field": "METADATA", "idMetadata": 11329, "filter": { "type": "IN", "values": ["FIT-100234"] } },
                    { "field": "METADATA", "idMetadata": 11331, "filter": { "type": "DATE_RANGE", "start": "31/12/2027", "end": "01/01/2028" } }
                  ],
                  "pagination": { "itemsPerPage": 25, "page": 1 },
                  "sort": [ { "field": "CREATION_DATE", "order": "DESC" } ]
                }
                """);
    }

    @Test
    void numbersBooleansListsAndSublists() {
        OpenBeeSearchRequest request = builder.build(43, List.of(
                vm(1, MetadataTypeGroup.INTEGER, BigInteger.valueOf(7)),
                vm(2, MetadataTypeGroup.DECIMAL, new BigDecimal("1.50")),
                vm(3, MetadataTypeGroup.BOOLEAN, Boolean.TRUE),
                vm(4, MetadataTypeGroup.BOOLEAN, Boolean.FALSE),
                vm(5, MetadataTypeGroup.LIST, new CatalogMetadata.ListValue(101, "Passport", null),
                        new CatalogMetadata.ListValue(102, "Emirates ID", null)),
                vm(6, MetadataTypeGroup.SUBLIST, "Dubai", "Abu Dhabi")), 3, 10);

        assertJson(request, """
                {
                  "filters": [
                    { "field": "FILING_CATEGORY", "filter": { "type": "EQUALS", "value": 43 } },
                    { "field": "TYPE", "filter": { "type": "IN", "values": ["PDF", "OFFICE", "IMAGE", "AUDIO", "OTHERS"] } },
                    { "field": "METADATA", "idMetadata": 1, "filter": { "type": "COMPARISON", "comparator": "=", "value": 7 } },
                    { "field": "METADATA", "idMetadata": 2, "filter": { "type": "COMPARISON", "comparator": "=", "value": 1.50 } },
                    { "field": "METADATA", "idMetadata": 3, "filter": { "type": "BOOLEAN", "includeTrue": true, "includeFalse": false, "includeEmpty": false } },
                    { "field": "METADATA", "idMetadata": 4, "filter": { "type": "BOOLEAN", "includeTrue": false, "includeFalse": true, "includeEmpty": false } },
                    { "field": "METADATA", "idMetadata": 5, "filter": { "type": "IN", "values": ["Passport", "Emirates ID"] } },
                    { "field": "METADATA", "idMetadata": 6, "filter": { "type": "IN", "values": ["Dubai", "Abu Dhabi"] } }
                  ],
                  "pagination": { "itemsPerPage": 10, "page": 3 },
                  "sort": [ { "field": "CREATION_DATE", "order": "DESC" } ]
                }
                """);
    }

    @Test
    void listById_followsTheUploadListValueSwitch() {
        properties.getOpenbee().setUploadListValue(DmsProperties.UploadListValue.ID);

        OpenBeeSearchRequest request = builder.build(42, List.of(
                vm(5, MetadataTypeGroup.LIST, new CatalogMetadata.ListValue(101, "Passport", null))), 1, 25);

        assertThat(request.filters().get(2).filter().values()).containsExactly("101");
    }

    @Test
    void dateRange_crossesMonthAndLeapDay() {
        OpenBeeSearchRequest request = builder.build(42, List.of(vm(9, MetadataTypeGroup.DATE, LocalDate.of(2028, 2, 28))), 1, 25);

        assertThat(request.filters().get(2).filter().start()).isEqualTo("28/02/2028");
        assertThat(request.filters().get(2).filter().end()).isEqualTo("29/02/2028");
    }

    private static void assertJson(OpenBeeSearchRequest request, String expected) {
        assertThat(JSON.readTree(JSON.writeValueAsString(request))).isEqualTo(JSON.readTree(expected));
    }

    private static ValidatedMetadata vm(long id, MetadataTypeGroup group, Object... values) {
        return new ValidatedMetadata(new CatalogMetadata(id, "M" + id, group.name(), group, false, values.length > 1,
                false, null, null, null), List.of(values));
    }
}
