package com.alramz.openbee;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Advanced Search response. Fields this service must not expose (creator, modifiedBy, idDocumentVersion,
 * idParentFolder, folderType, isHidden, aggregations) are not declared. Timestamps stay strings and are
 * converted by {@code OpenBeeTimestampConverter} (the live Open Bee sends them with a UTC offset).
 * {@code idMetadata} is a string in Open Bee but may arrive as a number (V6); both bind to the String field.
 * The live Open Bee returns the hits under {@code items}; the vendor spec calls them {@code results}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenBeeSearchResponse(@JsonAlias("items") List<Item> results, Pagination pagination) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Long idFolder, Long idDocument, String name, String creationDate, String modificationDate,
                       Long size, Long idFilingCategory, String nameFilingCategory, String extension,
                       String documentPath, List<MetadataValue> metadata) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MetadataValue(String idMetadata, String value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagination(Long count, Integer nbPages, Integer itemsPerPage, Integer page) {
    }
}
