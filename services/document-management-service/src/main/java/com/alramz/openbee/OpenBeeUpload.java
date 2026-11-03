package com.alramz.openbee;

import com.alramz.service.ValidatedMetadata;

import java.nio.file.Path;
import java.util.List;

/** One upload to {@code POST /browse/ws/document}. toString() omits the file name and metadata values. */
public record OpenBeeUpload(Path file, String fileName, String description, long filingCategoryId,
                            List<ValidatedMetadata> metadata, boolean overwrite, String sha256Hex) {

    public OpenBeeUpload {
        metadata = metadata == null ? List.of() : List.copyOf(metadata);
    }

    @Override
    public String toString() {
        return "OpenBeeUpload[filingCategoryId=" + filingCategoryId + ", metadataCount=" + metadata.size()
                + ", overwrite=" + overwrite + "]";
    }
}
