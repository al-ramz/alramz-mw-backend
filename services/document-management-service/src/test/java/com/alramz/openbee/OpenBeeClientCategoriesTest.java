package com.alramz.openbee;

import com.alramz.logging.util.MDCUtil;
import com.alramz.service.ConsumerContext;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Brief 11.2, categories and lists, against a WireMock Open Bee. */
class OpenBeeClientCategoriesTest {

    private static final ConsumerContext ONBOARDING =
            new ConsumerContext("onboarding-portal", OpenBeeWireMockSupport.KEY_ONBOARDING);
    private static final ConsumerContext CRM = new ConsumerContext("crm", OpenBeeWireMockSupport.KEY_CRM);

    private OpenBeeWireMockSupport openBee;

    @BeforeEach
    void setUp() {
        openBee = new OpenBeeWireMockSupport();
    }

    @AfterEach
    void tearDown() {
        openBee.close();
        MDC.clear();
    }

    @Test
    void listFilingCategories_sendsQueryParamsAndExactlyOneKeyAuthorization() {
        openBee.server().stubFor(get(urlPathEqualTo(OpenBeeClient.EP_CATEGORIES))
                .willReturn(okJson(OpenBeeWireMockSupport.CATEGORIES_PAGE)));

        OpenBeeCategoryPage page = openBee.client().listFilingCategories(ONBOARDING, 2, 50);

        assertThat(page.pagination().nbPages()).isEqualTo(1);
        assertThat(page.results()).hasSize(2);
        OpenBeeFilingCategory first = page.results().get(0).resolve();
        assertThat(first.id()).isEqualTo(42L);
        assertThat(first.rule().grammarTree()).isEqualTo("/Clients/[CLIENT_ID]");
        assertThat(first.metadata()).extracting(OpenBeeFilingCategory.Metadata::idList).containsExactly(null, 7L, null, null);
        assertThat(first.metadata().get(3).autoincrement()).isTrue();

        openBee.server().verify(1, getRequestedFor(urlPathEqualTo(OpenBeeClient.EP_CATEGORIES))
                .withQueryParam("taxonomies", equalTo("Core"))
                .withQueryParam("permissions", equalTo("use"))
                .withQueryParam("itemsPerPage", equalTo("50"))
                .withQueryParam("page", equalTo("2"))
                .withQueryParam("order", equalTo("name"))
                .withQueryParam("direction", equalTo("ASC")));
        LoggedRequest request = openBee.server().getAllServeEvents().get(0).getRequest();
        assertThat(request.getHeaders().getHeader("Authorization").values()).containsExactly("Key test-key-onboarding");
    }

    @Test
    void eachConsumerSendsItsOwnKey_neverBearer() {
        openBee.server().stubFor(get(urlPathEqualTo(OpenBeeClient.EP_CATEGORIES))
                .willReturn(okJson(OpenBeeWireMockSupport.CATEGORIES_PAGE)));

        openBee.client().listFilingCategories(ONBOARDING, 1, 50);
        openBee.client().listFilingCategories(CRM, 1, 50);

        List<String> auth = openBee.server().getAllServeEvents().stream()
                .map(e -> e.getRequest().getHeaders().getHeader("Authorization").values())
                .peek(values -> assertThat(values).hasSize(1))
                .map(values -> values.get(0))
                .toList();
        assertThat(auth).containsExactlyInAnyOrder("Key test-key-onboarding", "Key test-key-crm");
        assertThat(auth).noneMatch(v -> v.startsWith("Bearer"));
    }

    @Test
    void unwrappedItems_areAccepted() {
        openBee.server().stubFor(get(urlPathEqualTo(OpenBeeClient.EP_CATEGORIES)).willReturn(okJson("""
                { "pagination": { "count": 1, "nbPages": 1 },
                  "results": [ { "id": 44, "name": "Unwrapped", "metadata": [], "rule": { "active": false } } ] }
                """)));

        OpenBeeCategoryPage page = openBee.client().listFilingCategories(ONBOARDING, 1, 50);

        assertThat(page.results().get(0).resolve().id()).isEqualTo(44L);
        assertThat(page.results().get(0).resolve().rule().active()).isFalse();
    }

    @Test
    void getList_sendsRecursiveFalse() {
        openBee.server().stubFor(get(urlPathEqualTo("/filing/ws/list/7"))
                .willReturn(okJson(OpenBeeWireMockSupport.LIST_7)));

        OpenBeeList list = openBee.client().getList(ONBOARDING, 7);

        assertThat(list.values()).extracting(OpenBeeList.Value::name).containsExactly("Passport", "Emirates ID");
        openBee.server().verify(1, getRequestedFor(urlPathEqualTo("/filing/ws/list/7"))
                .withQueryParam("recursive", equalTo("false"))
                .withHeader("Authorization", equalTo("Key test-key-onboarding")));
    }

    @Test
    void correlationId_isPropagated() {
        MDCUtil.putCorrelationId("3f2c1a9e-7b4d-4c1e-9a6f-2d8e5b7c0a11");
        openBee.server().stubFor(get(urlPathEqualTo("/filing/ws/list/7"))
                .willReturn(okJson(OpenBeeWireMockSupport.LIST_7)));

        openBee.client().getList(ONBOARDING, 7);

        openBee.server().verify(getRequestedFor(urlPathEqualTo("/filing/ws/list/7"))
                .withHeader("X-Correlation-ID", equalTo("3f2c1a9e-7b4d-4c1e-9a6f-2d8e5b7c0a11")));
    }

    @Test
    void serverError_isRetriedToMaxAttempts() {
        openBee.server().stubFor(get(urlPathEqualTo(OpenBeeClient.EP_CATEGORIES))
                .willReturn(aResponse().withStatus(500).withBody("<html>oops</html>")));

        assertThatThrownBy(() -> openBee.client().listFilingCategories(ONBOARDING, 1, 50))
                .isInstanceOfSatisfying(OpenBeeException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(OpenBeeException.Kind.HTTP_ERROR);
                    assertThat(e.getHttpStatus()).isEqualTo(500);
                    assertThat(e.getObCodes()).isEmpty();
                    assertThat(e.isUnavailable()).isTrue();
                });
        openBee.server().verify(3, getRequestedFor(urlPathEqualTo(OpenBeeClient.EP_CATEGORIES)));
    }

    @Test
    void badRequest_isNotRetriedAndCodesAreParsed() {
        openBee.server().stubFor(get(urlPathEqualTo(OpenBeeClient.EP_CATEGORIES))
                .willReturn(aResponse().withStatus(400).withBody(OpenBeeWireMockSupport.CATEGORY_NOT_FOUND)));

        assertThatThrownBy(() -> openBee.client().listFilingCategories(ONBOARDING, 1, 50))
                .isInstanceOfSatisfying(OpenBeeException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(400);
                    assertThat(e.getObCodes()).containsExactly("FILING_CATEGORY_IS_NOT_FOUND");
                    assertThat(e.getMessage()).doesNotContain("Filing category was not found");
                });
        openBee.server().verify(1, getRequestedFor(urlPathEqualTo(OpenBeeClient.EP_CATEGORIES)));
    }

    @Test
    void unauthorized_doesNotAffectTheCircuitBreaker() {
        openBee.server().stubFor(get(urlPathEqualTo(OpenBeeClient.EP_CATEGORIES))
                .willReturn(aResponse().withStatus(401).withBody(OpenBeeWireMockSupport.BAD_CREDENTIALS)));

        assertThatThrownBy(() -> openBee.client().listFilingCategories(ONBOARDING, 1, 50))
                .isInstanceOfSatisfying(OpenBeeException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(401);
                    assertThat(e.hasCode("BAD_CREDENTIALS")).isTrue();
                });
        assertThat(openBee.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(openBee.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isZero();
        openBee.server().verify(1, getRequestedFor(urlPathEqualTo(OpenBeeClient.EP_CATEGORIES)));
    }

    @Test
    void serverErrors_areRecordedByTheCircuitBreaker() {
        openBee.server().stubFor(get(urlPathEqualTo("/filing/ws/list/7")).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> openBee.client().getList(ONBOARDING, 7)).isInstanceOf(OpenBeeException.class);

        assertThat(openBee.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isEqualTo(3);
    }

    @Test
    void openCircuit_failsFastWithoutCallingOpenBee() {
        openBee.circuitBreaker().transitionToOpenState();

        assertThatThrownBy(() -> openBee.client().getList(ONBOARDING, 7))
                .isInstanceOfSatisfying(OpenBeeException.class,
                        e -> assertThat(e.getKind()).isEqualTo(OpenBeeException.Kind.CIRCUIT_OPEN));
        assertThat(openBee.server().getAllServeEvents()).isEmpty();
    }

    @Test
    void unparseableBody_isBadResponse() {
        openBee.server().stubFor(get(urlPathEqualTo("/filing/ws/list/7"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{not json")));

        assertThatThrownBy(() -> openBee.client().getList(ONBOARDING, 7))
                .isInstanceOfSatisfying(OpenBeeException.class,
                        e -> assertThat(e.getKind()).isEqualTo(OpenBeeException.Kind.BAD_RESPONSE));
        openBee.server().verify(1, getRequestedFor(urlPathEqualTo("/filing/ws/list/7")));
    }

    @Test
    void htmlBody_isBadResponse() {
        openBee.server().stubFor(get(urlPathEqualTo("/filing/ws/list/7"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/html").withBody("<html/>")));

        assertThatThrownBy(() -> openBee.client().getList(ONBOARDING, 7))
                .isInstanceOfSatisfying(OpenBeeException.class,
                        e -> assertThat(e.getKind()).isEqualTo(OpenBeeException.Kind.BAD_RESPONSE));
    }

    @Test
    void emptyBody_isBadResponse() {
        openBee.server().stubFor(get(urlPathEqualTo("/filing/ws/list/7"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")));

        assertThatThrownBy(() -> openBee.client().getList(ONBOARDING, 7))
                .isInstanceOfSatisfying(OpenBeeException.class,
                        e -> assertThat(e.getKind()).isEqualTo(OpenBeeException.Kind.BAD_RESPONSE));
    }

    @Test
    void slowResponse_isTimeoutAndRetried() {
        try (OpenBeeWireMockSupport slow = new OpenBeeWireMockSupport(Duration.ofMillis(400), Duration.ofSeconds(5))) {
            slow.server().stubFor(get(urlPathEqualTo("/filing/ws/list/7"))
                    .willReturn(okJson(OpenBeeWireMockSupport.LIST_7).withFixedDelay(3000)));

            assertThatThrownBy(() -> slow.client().getList(ONBOARDING, 7))
                    .isInstanceOfSatisfying(OpenBeeException.class,
                            e -> assertThat(e.getKind()).isEqualTo(OpenBeeException.Kind.TIMEOUT));
            assertThat(slow.server().countRequestsMatching(getRequestedFor(urlPathEqualTo("/filing/ws/list/7")).build())
                    .getCount()).isEqualTo(3);
        }
    }

    @Test
    void connectionRefused_isConnectionError() {
        int port = openBee.server().port();
        openBee.server().stop();

        assertThatThrownBy(() -> openBee.client().getList(ONBOARDING, 7))
                .isInstanceOfSatisfying(OpenBeeException.class, e -> {
                    assertThat(e.getKind()).isIn(OpenBeeException.Kind.CONNECTION, OpenBeeException.Kind.TIMEOUT);
                    assertThat(e.getHttpStatus()).isZero();
                    assertThat(e.getMessage()).doesNotContain(String.valueOf(port));
                });
    }

    @Test
    void errorCodes_toleratesGarbage() {
        assertThat(OpenBeeClient.errorCodes(null)).isEmpty();
        assertThat(OpenBeeClient.errorCodes(new byte[0])).isEmpty();
        assertThat(OpenBeeClient.errorCodes("not json".getBytes())).isEmpty();
        assertThat(OpenBeeClient.errorCodes("{\"error\":[null,{\"message\":\"x\"},{\"code\":\"A\"}]}".getBytes()))
                .containsExactly("A");
    }

    /** The live Open Bee sends {@code error} as a single object, not the documented array. */
    @Test
    void errorCodes_acceptsASingleErrorObject() {
        assertThat(OpenBeeClient.errorCodes(
                "{\"error\":{\"code\":\"Document with the same name already exist\",\"message\":\"m\"}}".getBytes()))
                .containsExactly("Document with the same name already exist");
        assertThat(OpenBeeClient.errorCodes("{\"error\":{\"code\":\"MUST_SPECIFY_FOLDER_OR_RULE\"}}".getBytes()))
                .containsExactly("MUST_SPECIFY_FOLDER_OR_RULE");
    }

    @Test
    void translate_mapsUnknownFailures() {
        assertThat(OpenBeeClient.translate(new IllegalStateException("x", new java.io.IOException("io")), "/x"))
                .isInstanceOfSatisfying(OpenBeeException.class,
                        e -> assertThat(e.getKind()).isEqualTo(OpenBeeException.Kind.CONNECTION));
        assertThat(OpenBeeClient.translate(new IllegalStateException("x"), "/x"))
                .isInstanceOfSatisfying(OpenBeeException.class,
                        e -> assertThat(e.getKind()).isEqualTo(OpenBeeException.Kind.BAD_RESPONSE));
    }
}
