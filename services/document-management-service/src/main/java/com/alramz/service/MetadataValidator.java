package com.alramz.service;

import com.alramz.exception.DmsException;
import com.alramz.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Validates client metadata against a cached filing category (brief 8.1/8.2). Messages name the metadata ID and
 * name only, never the value (values can be personal data). Numbers are normalised through BigInteger /
 * BigDecimal parsing of their text, never Double arithmetic.
 */
@Component
public class MetadataValidator {

    static final int MAX_TEXT_LENGTH = 4000;
    /** Same bound as Jackson's default maximum JSON number length: any plain-written number stays valid. */
    static final int MAX_NUMBER_DIGITS = 1000;
    private static final Pattern NUMERIC_ID = Pattern.compile("\\d{1,18}");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    /** Save rules 1-7. */
    public List<ValidatedMetadata> validateForSave(CatalogCategory category, Map<String, Object> metadata) {
        Map<Long, Object> byId = resolveIds(category, metadata);
        for (CatalogMetadata definition : category.metadata()) {
            if (definition.mandatory() && definition.group() == MetadataTypeGroup.UNSUPPORTED) {
                throw unsupported(definition);
            }
        }
        List<ValidatedMetadata> result = new ArrayList<>();
        for (Map.Entry<Long, Object> entry : byId.entrySet()) {
            CatalogMetadata definition = category.metadata(entry.getKey()).orElseThrow();
            Object value = entry.getValue();
            if (definition.group() == MetadataTypeGroup.UNSUPPORTED) {
                throw unsupported(definition);
            }
            if (isAbsent(value)) {
                continue;
            }
            if (definition.readOnly()) {
                throw new DmsException(ErrorCode.DMS105,
                        "Metadata " + definition.label() + " is read-only and must not be sent");
            }
            if (value instanceof Collection<?> && !definition.multiple()) {
                throw multipleNotSupported(definition);
            }
            result.add(new ValidatedMetadata(definition, normaliseAll(definition, value)));
        }
        for (CatalogMetadata definition : category.metadata()) {
            if (definition.mandatory() && !definition.readOnly() && isAbsent(byId.get(definition.id()))) {
                throw new DmsException(ErrorCode.DMS104, "Mandatory metadata " + definition.label() + " is missing");
            }
        }
        return result;
    }

    /** Search rules 1, 4 (arrays only for text/list/sublist), 5, 6, 7; no mandatory check; readOnly allowed. */
    public List<ValidatedMetadata> validateForSearch(CatalogCategory category, Map<String, Object> metadata) {
        Map<Long, Object> byId = resolveIds(category, metadata);
        List<ValidatedMetadata> result = new ArrayList<>();
        for (Map.Entry<Long, Object> entry : byId.entrySet()) {
            CatalogMetadata definition = category.metadata(entry.getKey()).orElseThrow();
            Object value = entry.getValue();
            if (definition.group() == MetadataTypeGroup.UNSUPPORTED) {
                throw unsupported(definition);
            }
            if (isAbsent(value)) {
                continue;
            }
            if (value instanceof Collection<?> && !definition.group().supportsMultiValueSearch()) {
                throw multipleNotSupported(definition);
            }
            result.add(new ValidatedMetadata(definition, normaliseAll(definition, value)));
        }
        return result;
    }

    /** The first metadata key that is numeric but not defined on the category (triggers a forced refresh). */
    public Optional<Long> firstUnknownId(CatalogCategory category, Map<String, Object> metadata) {
        if (metadata == null) {
            return Optional.empty();
        }
        for (String key : metadata.keySet()) {
            if (key != null && NUMERIC_ID.matcher(key.trim()).matches()) {
                long id = Long.parseLong(key.trim());
                if (category.metadata(id).isEmpty()) {
                    return Optional.of(id);
                }
            }
        }
        return Optional.empty();
    }

    /** Rule 1: every key is a numeric ID defined on the category. */
    private static Map<Long, Object> resolveIds(CatalogCategory category, Map<String, Object> metadata) {
        Map<Long, Object> byId = new LinkedHashMap<>();
        if (metadata == null) {
            return byId;
        }
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            if (!NUMERIC_ID.matcher(key).matches()) {
                throw new DmsException(ErrorCode.DMS103, "Metadata keys must be numeric metadata IDs");
            }
            long id = Long.parseLong(key);
            if (category.metadata(id).isEmpty()) {
                throw new DmsException(ErrorCode.DMS103,
                        "Metadata " + id + " is not defined on filing category " + category.id());
            }
            byId.put(id, entry.getValue());
        }
        return byId;
    }

    private static List<Object> normaliseAll(CatalogMetadata definition, Object value) {
        List<Object> normalised = new ArrayList<>();
        if (value instanceof Collection<?> values) {
            for (Object item : values) {
                if (item == null || item instanceof Collection<?> || item instanceof Map<?, ?>) {
                    throw invalid(definition);
                }
                normalised.add(normalise(definition, item));
            }
        } else {
            normalised.add(normalise(definition, value));
        }
        return normalised;
    }

    /** Rules 5 and 6: one value against its type group. */
    static Object normalise(CatalogMetadata definition, Object value) {
        return switch (definition.group()) {
            case TEXT, SUBLIST -> text(definition, value);
            case INTEGER -> integer(definition, value);
            case DECIMAL -> decimal(definition, value);
            case DATE -> date(definition, value);
            case BOOLEAN -> bool(definition, value);
            case LIST -> listValue(definition, value);
            case UNSUPPORTED -> throw unsupported(definition);
        };
    }

    private static String text(CatalogMetadata definition, Object value) {
        if (!(value instanceof String text) || text.isBlank() || text.length() > MAX_TEXT_LENGTH) {
            throw invalid(definition);
        }
        return text; // byte-for-byte: no trimming, no case change
    }

    private static BigInteger integer(CatalogMetadata definition, Object value) {
        try {
            return decimalOf(value).toBigIntegerExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw invalid(definition);
        }
    }

    private static BigDecimal decimal(CatalogMetadata definition, Object value) {
        try {
            return decimalOf(value);
        } catch (NumberFormatException e) {
            throw invalid(definition);
        }
    }

    private static BigDecimal decimalOf(Object value) {
        BigDecimal number = switch (value) {
            case BigDecimal d -> d;
            case BigInteger i -> new BigDecimal(i);
            case Long l -> BigDecimal.valueOf(l);
            case Integer i -> BigDecimal.valueOf(i);
            case Short s -> BigDecimal.valueOf(s);
            case Double d when Double.isFinite(d) -> new BigDecimal(Double.toString(d));
            case Float f when Float.isFinite(f) -> new BigDecimal(Float.toString(f));
            case String text -> plainNumber(text.trim());
            default -> throw new NumberFormatException("not a number");
        };
        return withinDigitLimit(number);
    }

    /**
     * Rejects a magnitude or scale that no plain number of at most {@link #MAX_NUMBER_DIGITS} digits can have. An
     * exponent such as {@code 1e999999999} (the save form's metadata binds decimals to BigDecimal) is cheap to parse,
     * but {@code toBigIntegerExact()} or {@code toPlainString()} on it would then take minutes and hundreds of MB.
     */
    private static BigDecimal withinDigitLimit(BigDecimal number) {
        long integerDigits = (long) number.precision() - number.scale();
        if (number.scale() > MAX_NUMBER_DIGITS || integerDigits > MAX_NUMBER_DIGITS) {
            throw new NumberFormatException("number out of range");
        }
        return number;
    }

    /** A numeric string with a '.' decimal separator and no exponent. */
    private static BigDecimal plainNumber(String text) {
        if (text.isEmpty() || text.length() > MAX_NUMBER_DIGITS || text.indexOf('e') >= 0 || text.indexOf('E') >= 0) {
            throw new NumberFormatException("not a plain number");
        }
        return new BigDecimal(text);
    }

    private static LocalDate date(CatalogMetadata definition, Object value) {
        if (value instanceof String text) {
            try {
                return LocalDate.parse(text, ISO_DATE);
            } catch (DateTimeParseException e) {
                throw invalid(definition);
            }
        }
        throw invalid(definition);
    }

    private static Boolean bool(CatalogMetadata definition, Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if ("true".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equals(value)) {
            return Boolean.FALSE;
        }
        throw invalid(definition);
    }

    /**
     * Rule 6: exact, case-sensitive name match first, then id match. A list that allows new values (Open Bee
     * {@code addNewValue}) is not cached: any text (or whole number) is accepted and sent as entered.
     */
    private static Object listValue(CatalogMetadata definition, Object value) {
        if (definition.allowNewValue()) {
            return newListValue(definition, value);
        }
        return knownListValue(definition, value);
    }

    /** Open Bee stores a new value in the shared list permanently: no control characters (line breaks, tabs, ...). */
    private static String newListValue(CatalogMetadata definition, Object value) {
        if (value instanceof Number) {
            return integer(definition, value).toString();
        }
        String text = text(definition, value);
        if (text.chars().anyMatch(Character::isISOControl)) {
            throw invalid(definition);
        }
        return text;
    }

    private static CatalogMetadata.ListValue knownListValue(CatalogMetadata definition, Object value) {
        List<CatalogMetadata.ListValue> values = definition.listValues() == null ? List.of() : definition.listValues();
        if (value instanceof String name) {
            Optional<CatalogMetadata.ListValue> byName = values.stream().filter(v -> name.equals(v.name())).findFirst();
            if (byName.isPresent()) {
                return byName.get();
            }
        }
        if (value instanceof Number || value instanceof String) {
            String text = value.toString().trim();
            if (NUMERIC_ID.matcher(text).matches()) {
                long id = Long.parseLong(text);
                Optional<CatalogMetadata.ListValue> byId = values.stream().filter(v -> v.id() == id).findFirst();
                if (byId.isPresent()) {
                    return byId.get();
                }
            }
        }
        throw invalid(definition);
    }

    private static boolean isAbsent(Object value) {
        return value == null
                || value instanceof Collection<?> c && c.isEmpty()
                || value instanceof String s && s.isEmpty();
    }

    private static DmsException invalid(CatalogMetadata definition) {
        return new DmsException(ErrorCode.DMS105, "Invalid value for metadata " + definition.label());
    }

    private static DmsException multipleNotSupported(CatalogMetadata definition) {
        return new DmsException(ErrorCode.DMS105, "Multiple values are not supported for metadata " + definition.label());
    }

    private static DmsException unsupported(CatalogMetadata definition) {
        return new DmsException(ErrorCode.DMS110,
                "Metadata " + definition.label() + " has a type not supported by this service");
    }
}
