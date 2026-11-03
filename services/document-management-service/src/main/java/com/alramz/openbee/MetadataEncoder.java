package com.alramz.openbee;

import com.alramz.config.DmsProperties;
import com.alramz.service.CatalogMetadata;
import com.alramz.service.ValidatedMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Open Bee upload encoding of validated metadata (brief 8.3): one {@code metadatas[<id>]} field for a single
 * value, a repeated {@code metadatas[<id>][]} field per value for a multiple metadata. Value formats are
 * config-driven (V1): text as received, numbers plain with '.', dates per {@code upload-date-format}, booleans
 * {@code 1/0} or {@code true/false}, list values by name or id.
 */
@Component
@RequiredArgsConstructor
public class MetadataEncoder {

    private final DmsProperties properties;

    /** Ordered multipart field name / value pairs. */
    public List<Map.Entry<String, String>> encode(List<ValidatedMetadata> metadata) {
        List<Map.Entry<String, String>> fields = new ArrayList<>();
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(properties.getOpenbee().getUploadDateFormat());
        for (ValidatedMetadata entry : metadata) {
            CatalogMetadata definition = entry.definition();
            String field = definition.multiple()
                    ? "metadatas[" + definition.id() + "][]"
                    : "metadatas[" + definition.id() + "]";
            for (Object value : entry.normalisedValues()) {
                fields.add(new AbstractMap.SimpleImmutableEntry<>(field, format(value, dateFormat)));
            }
        }
        return fields;
    }

    String format(Object value, DateTimeFormatter dateFormat) {
        return switch (value) {
            case BigDecimal d -> d.toPlainString();
            case BigInteger i -> i.toString();
            case LocalDate date -> dateFormat.format(date);
            case Boolean b -> booleanValue(b);
            case CatalogMetadata.ListValue listValue -> listValue(listValue);
            default -> value.toString(); // text and sublist: as received
        };
    }

    String booleanValue(boolean value) {
        return properties.getOpenbee().getUploadBooleanFormat() == DmsProperties.UploadBooleanFormat.TRUE_FALSE
                ? Boolean.toString(value)
                : value ? "1" : "0";
    }

    /** A list value in the form Open Bee expects (also used for search LIST filters). */
    public String listValue(CatalogMetadata.ListValue value) {
        return properties.getOpenbee().getUploadListValue() == DmsProperties.UploadListValue.ID
                ? String.valueOf(value.id())
                : value.name();
    }
}
