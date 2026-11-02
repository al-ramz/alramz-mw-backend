package com.alramz.openbee;

import com.alramz.config.DmsProperties;
import com.alramz.service.CatalogMetadata;
import com.alramz.service.MetadataTypeGroup;
import com.alramz.service.ValidatedMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Brief 8.3: field names, multi-value repetition, every type group and config switch. */
class MetadataEncoderTest {

    private static final CatalogMetadata.ListValue PASSPORT = new CatalogMetadata.ListValue(101, "Passport", null);
    private static final CatalogMetadata.ListValue EID = new CatalogMetadata.ListValue(102, "Emirates ID", null);

    private DmsProperties properties;
    private MetadataEncoder encoder;

    @BeforeEach
    void setUp() {
        properties = new DmsProperties();
        encoder = new MetadataEncoder(properties);
    }

    @Test
    void defaults_encodeEachTypeGroup() {
        List<Map.Entry<String, String>> fields = encoder.encode(List.of(
                vm(11329, MetadataTypeGroup.TEXT, false, "FiT-100234 Ünïcode"),
                vm(11330, MetadataTypeGroup.LIST, false, PASSPORT),
                vm(11331, MetadataTypeGroup.DATE, false, LocalDate.of(2027, 12, 31)),
                vm(11332, MetadataTypeGroup.INTEGER, false, new BigInteger("12345678901234567890")),
                vm(11333, MetadataTypeGroup.DECIMAL, false, new BigDecimal("1.0E+3")),
                vm(11334, MetadataTypeGroup.BOOLEAN, false, Boolean.TRUE),
                vm(11335, MetadataTypeGroup.BOOLEAN, false, Boolean.FALSE),
                vm(11336, MetadataTypeGroup.SUBLIST, false, "Dubai")));

        assertThat(fields).extracting(Map.Entry::getKey, Map.Entry::getValue).containsExactly(
                org.assertj.core.groups.Tuple.tuple("metadatas[11329]", "FiT-100234 Ünïcode"),
                org.assertj.core.groups.Tuple.tuple("metadatas[11330]", "Passport"),
                org.assertj.core.groups.Tuple.tuple("metadatas[11331]", "2027-12-31"),
                org.assertj.core.groups.Tuple.tuple("metadatas[11332]", "12345678901234567890"),
                org.assertj.core.groups.Tuple.tuple("metadatas[11333]", "1000"),
                org.assertj.core.groups.Tuple.tuple("metadatas[11334]", "1"),
                org.assertj.core.groups.Tuple.tuple("metadatas[11335]", "0"),
                org.assertj.core.groups.Tuple.tuple("metadatas[11336]", "Dubai"));
    }

    @Test
    void multiple_isARepeatedArrayField() {
        List<Map.Entry<String, String>> fields = encoder.encode(List.of(vm(11401, MetadataTypeGroup.LIST, true, PASSPORT, EID)));

        assertThat(fields).extracting(Map.Entry::getKey).containsExactly("metadatas[11401][]", "metadatas[11401][]");
        assertThat(fields).extracting(Map.Entry::getValue).containsExactly("Passport", "Emirates ID");
    }

    @Test
    void configSwitches_dateBooleanAndListForm() {
        properties.getOpenbee().setUploadDateFormat("dd/MM/yyyy");
        properties.getOpenbee().setUploadBooleanFormat(DmsProperties.UploadBooleanFormat.TRUE_FALSE);
        properties.getOpenbee().setUploadListValue(DmsProperties.UploadListValue.ID);

        List<Map.Entry<String, String>> fields = encoder.encode(List.of(
                vm(11331, MetadataTypeGroup.DATE, false, LocalDate.of(2027, 1, 5)),
                vm(11334, MetadataTypeGroup.BOOLEAN, false, Boolean.TRUE),
                vm(11335, MetadataTypeGroup.BOOLEAN, false, Boolean.FALSE),
                vm(11330, MetadataTypeGroup.LIST, false, EID)));

        assertThat(fields).extracting(Map.Entry::getValue).containsExactly("05/01/2027", "true", "false", "102");
        assertThat(encoder.listValue(PASSPORT)).isEqualTo("101");
    }

    @Test
    void empty_encodesNothing() {
        assertThat(encoder.encode(List.of())).isEmpty();
    }

    private static ValidatedMetadata vm(long id, MetadataTypeGroup group, boolean multiple, Object... values) {
        return new ValidatedMetadata(new CatalogMetadata(id, "M" + id, group.name(), group, false, multiple, false,
                null, null, null), List.of(values));
    }
}
