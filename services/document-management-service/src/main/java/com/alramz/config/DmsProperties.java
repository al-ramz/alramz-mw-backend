package com.alramz.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DMS / Open Bee settings; bound by {@link OpenBeeClientConfig}. Startup fails fast (via {@code @Validated})
 * when the base URL is blank, the time zone is missing/invalid, or any configured consumer has a blank key.
 * Binding errors name the property path only; a blank key has nothing to print.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = DmsProperties.PREFIX)
public class DmsProperties {

    public static final String PREFIX = "dms";

    @Valid
    private OpenBee openbee = new OpenBee();

    /** Consumer ID (= JWT subject, exact match) to its own Open Bee API key. Use bracketed YAML keys. */
    @Valid
    private Map<String, @Valid Consumer> consumers = new LinkedHashMap<>();

    @Valid
    private Cache cache = new Cache();

    @Valid
    private Upload upload = new Upload();

    @Valid
    private Search search = new Search();

    public enum UploadBooleanFormat { ONE_ZERO, TRUE_FALSE }

    public enum UploadListValue { NAME, ID }

    @Getter
    @Setter
    public static class OpenBee {
        @NotBlank
        private String baseUrl;
        /** Zone Open Bee uses for timestamps without an offset. */
        @NotNull
        private ZoneId timeZone;
        private Duration connectTimeout = Duration.ofSeconds(5);
        /** JSON calls. */
        private Duration readTimeout = Duration.ofSeconds(15);
        /** Upload, and download until headers arrive and between body buffers. */
        private Duration transferReadTimeout = Duration.ofSeconds(120);
        @Valid
        private Retry retry = new Retry();
        private String uploadDateFormat = "yyyy-MM-dd";
        private UploadBooleanFormat uploadBooleanFormat = UploadBooleanFormat.ONE_ZERO;
        private UploadListValue uploadListValue = UploadListValue.NAME;
    }

    @Getter
    @Setter
    public static class Retry {
        /** Read calls only; uploads are never retried. */
        @Min(1)
        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofMillis(500);
    }

    @Getter
    @Setter
    public static class Consumer {
        @NotBlank
        private String apiKey;

        @Override
        public String toString() {
            return "Consumer[apiKey=***]";
        }
    }

    @Getter
    @Setter
    public static class Cache {
        private Duration categoriesTtl = Duration.ofMinutes(15);
        private Duration listsTtl = Duration.ofMinutes(15);
        private Duration forcedRefreshMinInterval = Duration.ofSeconds(60);
        /** A cached consumer entry is reloaded in the background once it is this close to its TTL. */
        private Duration refreshAhead = Duration.ofMinutes(5);
        /** Open Bee list calls run in parallel up to this many at a time while loading the catalog. */
        @Min(1)
        @Max(32)
        private int listLoadConcurrency = 8;
        /**
         * A request waits at most this long for another request's catalog load of the same consumer; then it gets
         * the cached copy, or DMS203 when there is none. Keeps a slow Open Bee from piling up request threads.
         */
        @NotNull
        private Duration lockWait = Duration.ofSeconds(20);
        /** A catalog load is abandoned (cached copy kept) once it has run this long; checked between Open Bee calls. */
        @NotNull
        private Duration loadDeadline = Duration.ofMinutes(2);

        @AssertTrue(message = "dms.cache.refresh-ahead must be shorter than dms.cache.categories-ttl")
        public boolean isRefreshAheadShorterThanTtl() {
            return refreshAhead == null || categoriesTtl == null || refreshAhead.compareTo(categoriesTtl) < 0;
        }
    }

    @Getter
    @Setter
    public static class Upload {
        private DataSize maxFileSize = DataSize.ofMegabytes(10);
        private Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
    }

    @Getter
    @Setter
    public static class Search {
        @Min(1)
        private int defaultPageSize = 25;
        @Min(1)
        private int maxPageSize = 100;
    }
}
