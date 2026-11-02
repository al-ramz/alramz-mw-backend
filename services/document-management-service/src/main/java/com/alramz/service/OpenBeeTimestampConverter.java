package com.alramz.service;

import com.alramz.config.DmsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Open Bee sends local timestamps without an offset (e.g. {@code 2026-10-28T10:15:00}). They are interpreted in
 * {@code dms.openbee.time-zone} and returned with the offset ({@code 2026-10-28T10:15:00+04:00}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenBeeTimestampConverter {

    private final DmsProperties properties;

    /**
     * @param warned per-request flag: an unparseable value logs a WARN at most once per request
     * @return the timestamp with offset, or null when absent or unparseable
     */
    public OffsetDateTime toOffsetDateTime(String value, AtomicBoolean warned) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim().replace(' ', 'T');
        try {
            return LocalDateTime.parse(text).atZone(properties.getOpenbee().getTimeZone()).toOffsetDateTime();
        } catch (DateTimeParseException notLocal) {
            try {
                return OffsetDateTime.parse(text);
            } catch (DateTimeParseException e) {
                if (!warned.getAndSet(true)) {
                    log.warn("Unparseable Open Bee timestamp; returning null for it in this response");
                }
                return null;
            }
        }
    }
}
