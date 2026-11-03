package com.alramz.openbee;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Open Bee {@code FilingCategory_output}. Fields this service must not expose (type, reapply, creationDate,
 * idCreator, restrictions, defaultValues) are deliberately not declared. {@code addNewValue} is read so that
 * free-entry lists are not fetched (exposed as {@code allowNewValue}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenBeeFilingCategory(Long id, String name, List<Metadata> metadata, Rule rule) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    /** {@code addNewValue}: a LIST metadata that accepts values not yet in its list (Open Bee adds them). */
    public record Metadata(Long id, String name, String type, Boolean mandatory, Boolean multiple,
                           Boolean autoincrement, Long parentMetadata, Long idList, Boolean addNewValue) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rule(Boolean active, String grammarTree, String grammarName) {
    }
}
