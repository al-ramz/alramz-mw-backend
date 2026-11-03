package com.alramz.service;

import com.alramz.config.DmsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Spools an upload to {@code dms.upload.temp-dir} while computing its SHA-256, so the checksum header can be
 * sent before the body without holding the file in memory. The temp name is a random UUID + {@code .upload}.
 * The caller deletes the file in {@code finally}; on a spooling failure it is deleted here before rethrowing.
 */
@Component
@RequiredArgsConstructor
public class UploadSpooler {

    static final String SUFFIX = ".upload";

    private final DmsProperties properties;

    public SpooledUpload spool(MultipartFile file) {
        MessageDigest digest = sha256();
        Path temp = null;
        try {
            Path dir = properties.getUpload().getTempDir();
            Files.createDirectories(dir);
            temp = Files.createTempFile(dir, UUID.randomUUID().toString(), SUFFIX);
            long size;
            try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
                size = Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            return new SpooledUpload(temp, size, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new UncheckedIOException("Could not spool the upload to the temp directory", e);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e); // every Java platform must provide it
        }
    }

    /** Deletes a spooled file; never throws. */
    public static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // best effort; the OS temp cleaner is the backstop
        }
    }
}
