package com.alramz.service;

import java.nio.file.Path;

/** An upload spooled to a random temp file (the name never contains the client's file name). */
public record SpooledUpload(Path path, long size, String sha256Hex) {
}
