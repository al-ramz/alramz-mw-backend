package com.alramz.openbee;

import com.alramz.config.DmsProperties;
import com.alramz.config.OpenBeeClientConfig;
import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.interceptor.WebClientLoggingFilter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.Notifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Test helper (not a test): a programmatic WireMock server (dynamic port, silent notifier, so its request
 * dumps never reach captured logs) and an {@link OpenBeeClient} built through {@link OpenBeeClientConfig}'s
 * real factory methods (JDK connector, circuit breaker, read/write retries, time limiter) with a short backoff.
 * A programmatic server avoids depending on WireMock's JUnit 5 extension under JUnit Jupiter 6.
 */
public final class OpenBeeWireMockSupport implements AutoCloseable {

    public static final String KEY_ONBOARDING = "test-key-onboarding";
    public static final String KEY_CRM = "test-key-crm";

    /** Appendix C.1 (wrapped items). */
    public static final String CATEGORIES_PAGE = """
            {
              "pagination": { "count": 2, "nbPages": 1 },
              "results": [
                { "filingCategory": {
                    "id": 42, "name": "Customer Passport", "type": "Core",
                    "rule": { "active": true, "reapply": true, "grammarTree": "/Clients/[CLIENT_ID]", "grammarName": "PASSPORT_[CLIENT_ID]" },
                    "creationDate": "2026-10-01T09:00:00", "idCreator": 3,
                    "metadata": [
                      { "id": 11329, "name": "CLIENT_ID", "type": "VARCHAR", "mandatory": true, "multiple": false, "autoincrement": false, "addNewValue": false },
                      { "id": 11330, "name": "DOCUMENT_TYPE", "type": "LIST", "mandatory": true, "multiple": false, "autoincrement": false, "idList": 7 },
                      { "id": 11331, "name": "EXPIRY_DATE", "type": "DATE", "mandatory": false, "multiple": false, "autoincrement": false },
                      { "id": 11332, "name": "REFERENCE_NO", "type": "INT", "mandatory": false, "multiple": false, "autoincrement": true }
                    ] } },
                { "filingCategory": {
                    "id": 43, "name": "Signed Agreement", "type": "Core",
                    "rule": { "active": true, "reapply": false, "grammarTree": "/Clients/[CLIENT_ID]/Agreements", "grammarName": null },
                    "metadata": [
                      { "id": 11400, "name": "CLIENT_ID", "type": "VARCHAR", "mandatory": true, "multiple": false, "autoincrement": false },
                      { "id": 11401, "name": "PRODUCTS", "type": "LIST", "mandatory": false, "multiple": true, "autoincrement": false, "idList": 7 },
                      { "id": 11402, "name": "SIGNED", "type": "BOOLEAN", "mandatory": false, "multiple": false, "autoincrement": false },
                      { "id": 11403, "name": "REQUEST_ID", "type": "LIST", "mandatory": false, "multiple": true, "autoincrement": false, "idList": 8, "addNewValue": true }
                    ] } }
              ]
            }
            """;

    /** Appendix C.2. */
    public static final String LIST_7 = """
            { "id": 7, "name": "Document types",
              "values": [
                { "id": 101, "name": "Passport", "image": null, "idSublist": null },
                { "id": 102, "name": "Emirates ID", "image": null, "idSublist": null } ] }
            """;

    /** Appendix C.4 expected request (text 11329 FIT-100234 + date 11331 2027-12-31, page 1 of 25). */
    public static final String SEARCH_REQUEST_C4 = """
            {
              "filters": [
                { "field": "FILING_CATEGORY", "filter": { "type": "EQUALS", "value": 42 } },
                { "field": "TYPE", "filter": { "type": "IN", "values": ["PDF", "OFFICE", "IMAGE", "AUDIO", "OTHERS"] } },
                { "field": "METADATA", "idMetadata": 11329, "filter": { "type": "IN", "values": ["FIT-100234"] } },
                { "field": "METADATA", "idMetadata": 11331, "filter": { "type": "DATE_RANGE", "start": "31/12/2027", "end": "01/01/2028" } }
              ],
              "pagination": { "itemsPerPage": 25, "page": 1 },
              "sort": [ { "field": "CREATION_DATE", "order": "DESC" } ]
            }
            """;

    /** Appendix C.4 response. */
    public static final String SEARCH_RESPONSE = """
            { "results": [
                { "idFolder": null, "idDocumentVersion": 99120, "idDocument": 88213,
                  "name": "PASSPORT_FIT-100234.pdf", "creationDate": "2026-10-28T10:15:00",
                  "creator": { "fullname": "svc onboarding", "id": 12 },
                  "modifiedBy": { "fullname": "svc onboarding", "id": 12 },
                  "size": 248331, "modificationDate": "2026-10-28T10:15:00",
                  "idFilingCategory": 42, "nameFilingCategory": "Customer Passport", "extension": "pdf",
                  "folderType": null, "idParentFolder": 5501, "documentPath": "/Clients/FIT-100234", "isHidden": false,
                  "metadata": [
                    { "idMetadata": "11329", "value": "FIT-100234" },
                    { "idMetadata": "11330", "value": "Passport" },
                    { "idMetadata": "11331", "value": "2027-12-31" } ] },
                { "idFolder": 5501, "idDocument": null, "name": "FIT-100234", "folderType": "FOLDER" } ],
              "pagination": { "count": 1, "nbPages": 1, "itemsPerPage": 25, "page": 1 },
              "aggregations": [] }
            """;

    public static final String BAD_CREDENTIALS = "{\"error\":[{\"code\":\"BAD_CREDENTIALS\",\"message\":\"Invalid login or password\"}]}";
    public static final String PERMISSION_DENIED = "{\"error\":[{\"code\":\"PERMISSION_DENIED\",\"message\":\"You are not allowed to perform this operation\"}]}";
    public static final String CATEGORY_NOT_FOUND = "{\"error\":[{\"code\":\"FILING_CATEGORY_IS_NOT_FOUND\",\"message\":\"Filing category was not found\"}]}";

    private final WireMockServer server;
    private final DmsProperties properties;
    private final CircuitBreaker circuitBreaker;
    private final OpenBeeClient client;

    /** Generous timeouts so a cold JVM / Jetty start never trips them; timeout tests pass short ones. */
    public OpenBeeWireMockSupport() {
        this(Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    public OpenBeeWireMockSupport(Duration readTimeout, Duration transferReadTimeout) {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort().notifier(new SilentNotifier()));
        server.start();
        properties = properties(server.baseUrl());
        properties.getOpenbee().setReadTimeout(readTimeout);
        properties.getOpenbee().setTransferReadTimeout(transferReadTimeout);
        OpenBeeClientConfig config = new OpenBeeClientConfig(properties);
        WebClient webClient = config.openBeeWebClient(WebClient.builder(),
                WebClientLoggingFilter.of(new LoggingProperties()));
        circuitBreaker = config.openBeeCircuitBreaker(CircuitBreakerRegistry.ofDefaults());
        client = new OpenBeeClient(webClient, circuitBreaker,
                config.openBeeReadRetry(RetryRegistry.ofDefaults()),
                config.openBeeWriteRetry(RetryRegistry.ofDefaults()),
                config.openBeeTimeLimiter(TimeLimiterRegistry.ofDefaults()),
                properties,
                new MetadataEncoder(properties));
    }

    /** Properties as the fake test config, with a 10 ms retry backoff. */
    public static DmsProperties properties(String baseUrl) {
        DmsProperties properties = new DmsProperties();
        properties.getOpenbee().setBaseUrl(baseUrl);
        properties.getOpenbee().setTimeZone(ZoneId.of("Asia/Dubai"));
        properties.getOpenbee().getRetry().setInitialBackoff(Duration.ofMillis(10));
        DmsProperties.Consumer onboarding = new DmsProperties.Consumer();
        onboarding.setApiKey(KEY_ONBOARDING);
        DmsProperties.Consumer crm = new DmsProperties.Consumer();
        crm.setApiKey(KEY_CRM);
        properties.getConsumers().put("onboarding-portal", onboarding);
        properties.getConsumers().put("crm", crm);
        return properties;
    }

    public WireMockServer server() {
        return server;
    }

    public OpenBeeClient client() {
        return client;
    }

    public CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    public DmsProperties properties() {
        return properties;
    }

    @Override
    public void close() {
        server.stop();
    }

    /** Swallows WireMock's own logging so request dumps never reach captured output. */
    public static final class SilentNotifier implements Notifier {
        @Override
        public void info(String message) {
            // silent
        }

        @Override
        public void error(String message) {
            // silent
        }

        @Override
        public void error(String message, Throwable t) {
            // silent
        }
    }
}
