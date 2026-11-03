package com.alramz.service;

import org.springframework.http.MediaType;

import java.io.InputStream;

/** A download ready to stream: response headers plus an InputStream over Open Bee's body (not buffered). */
public record DmsDocumentContent(MediaType contentType, Long contentLength, String contentDisposition, InputStream body) {

    @Override
    public String toString() {
        return "DmsDocumentContent[contentType=" + contentType + ", contentLength=" + contentLength + "]";
    }
}
