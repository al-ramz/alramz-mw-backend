package com.alramz.openbee;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

import java.util.Optional;

/**
 * Open Bee's file response: status already checked, headers read, body not yet consumed. The caller must
 * consume or cancel {@code body} (e.g. through {@code DataBufferUtils.subscriberInputStream}, which releases
 * buffers on close).
 */
public record OpenBeeFile(MediaType contentType, Long contentLength, Optional<String> fileName, Flux<DataBuffer> body) {

    @Override
    public String toString() {
        return "OpenBeeFile[contentType=" + contentType + ", contentLength=" + contentLength + "]";
    }
}
