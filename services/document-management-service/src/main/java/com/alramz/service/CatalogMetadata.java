package com.alramz.service;

import java.util.List;

/**
 * A metadata definition of a cached filing category. {@code readOnly} is Open Bee's {@code autoincrement};
 * {@code allowNewValue} is Open Bee's {@code addNewValue} (a LIST that accepts any value; its list is not
 * fetched). {@code listValues} is null when the metadata has no list or allows new values.
 */
public record CatalogMetadata(long id, String name, String rawType, MetadataTypeGroup group, boolean mandatory,
                              boolean multiple, boolean readOnly, Long parentMetadataId, Long listId,
                              List<ListValue> listValues, boolean allowNewValue) {

    public CatalogMetadata {
        listValues = listValues == null ? null : List.copyOf(listValues);
    }

    /** A metadata whose list (if any) only accepts its existing values. */
    public CatalogMetadata(long id, String name, String rawType, MetadataTypeGroup group, boolean mandatory,
                           boolean multiple, boolean readOnly, Long parentMetadataId, Long listId,
                           List<ListValue> listValues) {
        this(id, name, rawType, group, mandatory, multiple, readOnly, parentMetadataId, listId, listValues, false);
    }

    public record ListValue(long id, String name, Long sublistId) {
    }

    /** "11329 (CLIENT_ID)" for client-facing messages: ID and name, never a value. */
    public String label() {
        return id + " (" + name + ")";
    }
}
