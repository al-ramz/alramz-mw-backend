package com.alramz.service;

import java.util.List;
import java.util.Optional;

/** An immutable, mapped Open Bee filing category as cached per consumer. */
public record CatalogCategory(long id, String name, String path, Boolean autoFiling, String fileNamePattern,
                              List<CatalogMetadata> metadata) {

    public CatalogCategory {
        metadata = metadata == null ? List.of() : List.copyOf(metadata);
    }

    public Optional<CatalogMetadata> metadata(long metadataId) {
        return metadata.stream().filter(m -> m.id() == metadataId).findFirst();
    }
}
