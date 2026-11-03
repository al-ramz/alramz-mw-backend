package com.alramz.service;

import com.alramz.exception.DmsException;
import com.alramz.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Resolves the stored document name (brief 6.2). An explicit {@code fileName} wins (the original extension is
 * appended when it has none); otherwise the uploaded file's own name, stripped of any client path. No case or
 * other transformation; names with separators or control characters, blank, or over 255 chars are DMS101.
 */
@Component
public class FileNameResolver {

    static final int MAX_LENGTH = 255;
    static final String INVALID_CHARS = "fileName must not contain path separators or control characters";

    public String resolve(String requestedName, String originalFilename) {
        String original = baseName(originalFilename);
        String name;
        if (requestedName != null) {
            name = requestedName.trim();
            String originalExtension = extension(original);
            if (!name.isEmpty() && extension(name) == null && originalExtension != null) {
                name = name + "." + originalExtension;
            }
        } else if (original != null) {
            name = original.trim();
        } else {
            throw new DmsException(ErrorCode.DMS101, "fileName is required");
        }
        validate(name);
        return name;
    }

    /**
     * {@code name.extension} unless the extension is blank or the name already ends with it (case-insensitive).
     * Open Bee keeps a document's name and extension apart ({@code testfile} + {@code txt}).
     */
    public static String withExtension(String name, String extension) {
        if (name == null || extension == null || extension.isBlank()) {
            return name;
        }
        String ext = extension.trim();
        return name.toLowerCase(Locale.ROOT).endsWith("." + ext.toLowerCase(Locale.ROOT)) ? name : name + "." + ext;
    }

    /** The part after the last '.', or null when there is none (or the name starts/ends with it). */
    public static String extension(String name) {
        if (name == null) {
            return null;
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1) : null;
    }

    static String baseName(String filename) {
        if (filename == null) {
            return null;
        }
        String base = filename.substring(Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\')) + 1);
        return base.isBlank() ? null : base;
    }

    private static void validate(String name) {
        if (name.isBlank()) {
            throw new DmsException(ErrorCode.DMS101, "fileName must not be blank");
        }
        if (name.length() > MAX_LENGTH) {
            throw new DmsException(ErrorCode.DMS101, "fileName must be at most 255 characters");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || Character.isISOControl(c)) {
                throw new DmsException(ErrorCode.DMS101, INVALID_CHARS);
            }
        }
    }
}
