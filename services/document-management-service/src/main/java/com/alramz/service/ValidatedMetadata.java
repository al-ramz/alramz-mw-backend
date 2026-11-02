package com.alramz.service;

import java.util.List;

/**
 * A metadata entry that passed validation, with its values normalised to typed Java values: String (text,
 * sublist), BigInteger (integer), BigDecimal (decimal), LocalDate (date), Boolean, or
 * {@link CatalogMetadata.ListValue} (list). Wire formats are applied in one place by the encoder / filter
 * builder. toString() never prints values.
 */
public record ValidatedMetadata(CatalogMetadata definition, List<Object> normalisedValues) {

    public ValidatedMetadata {
        normalisedValues = List.copyOf(normalisedValues);
    }

    @Override
    public String toString() {
        return "ValidatedMetadata[id=" + definition.id() + ", values=" + normalisedValues.size() + "]";
    }
}
