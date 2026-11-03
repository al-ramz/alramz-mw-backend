package com.alramz.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * {@code attachment; filename="<ascii-safe>"; filename*=UTF-8''<percent-encoded>} (RFC 6266 / RFC 5987).
 * Hand-built: Spring's {@code ContentDisposition.filename(name, UTF_8)} emits an RFC 2047 encoded-word in
 * {@code filename=}, not an ASCII-safe fallback for clients that ignore {@code filename*}.
 */
@Component
public class ContentDispositionFormatter {

    private static final String ATTR_CHARS = "!#$&+-.^_`|~";
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    public String attachment(String fileName) {
        return "attachment; filename=\"" + asciiFallback(fileName) + "\"; filename*=UTF-8''" + percentEncode(fileName);
    }

    /** Non-ASCII, quotes, backslashes and control characters become '_'. */
    static String asciiFallback(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            sb.append(c < 0x20 || c > 0x7e || c == '"' || c == '\\' ? '_' : c);
        }
        return sb.toString();
    }

    /** RFC 5987 attr-char percent-encoding of the UTF-8 bytes. */
    static String percentEncode(String name) {
        StringBuilder sb = new StringBuilder();
        for (byte b : name.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || ATTR_CHARS.indexOf(c) >= 0) {
                sb.append((char) c);
            } else {
                sb.append('%').append(HEX[c >> 4]).append(HEX[c & 0xf]);
            }
        }
        return sb.toString();
    }
}
