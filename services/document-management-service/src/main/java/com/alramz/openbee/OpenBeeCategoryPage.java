package com.alramz.openbee;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One page of {@code GET /filingCategories/ws/filingCategories}. Items are accepted both wrapped
 * ({@code {"filingCategory":{...}}}, the vendor schema) and unwrapped ({@code {"id":..,"name":..}}), since the
 * real shape is unverified (V6).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenBeeCategoryPage(Pagination pagination, List<Item> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagination(Long count, Integer nbPages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(OpenBeeFilingCategory filingCategory, Long id, String name,
                       List<OpenBeeFilingCategory.Metadata> metadata, OpenBeeFilingCategory.Rule rule) {

        /** The wrapped category if present, else the flattened fields; null when neither carries an id. */
        public OpenBeeFilingCategory resolve() {
            if (filingCategory != null) {
                return filingCategory;
            }
            return id == null ? null : new OpenBeeFilingCategory(id, name, metadata, rule);
        }
    }
}
