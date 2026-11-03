package com.alramz.openbee;

import com.alramz.service.CatalogMetadata;
import com.alramz.service.ConsumerContext;
import com.alramz.service.MetadataTypeGroup;
import com.alramz.service.ValidatedMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Brief 11.2 search / Appendix C.4 against a WireMock Open Bee. */
class OpenBeeClientSearchTest {

    private static final ConsumerContext CRM = new ConsumerContext("crm", OpenBeeWireMockSupport.KEY_CRM);
    private static final String PATH = "/advancedSearch/ws/advanced-search";
    private static final String APPENDIX_C4 = OpenBeeWireMockSupport.SEARCH_REQUEST_C4;

    private OpenBeeWireMockSupport openBee;
    private SearchFilterBuilder builder;

    @BeforeEach
    void setUp() {
        openBee = new OpenBeeWireMockSupport();
        builder = new SearchFilterBuilder(new MetadataEncoder(openBee.properties()));
    }

    @AfterEach
    void tearDown() {
        openBee.close();
    }

    @Test
    void advancedSearch_sendsTheExactAppendixC4Body_andParsesTheResponse() {
        openBee.server().stubFor(post(urlPathEqualTo(PATH))
                .withRequestBody(equalToJson(APPENDIX_C4))
                .willReturn(okJson(OpenBeeWireMockSupport.SEARCH_RESPONSE)));

        OpenBeeSearchResponse response = openBee.client().advancedSearch(CRM, appendixC4Request());

        assertThat(response.pagination().count()).isEqualTo(1L);
        assertThat(response.results()).hasSize(2);
        OpenBeeSearchResponse.Item document = response.results().get(0);
        assertThat(document.idDocument()).isEqualTo(88213L);
        assertThat(document.creationDate()).isEqualTo("2026-10-28T10:15:00");
        assertThat(document.metadata()).extracting(OpenBeeSearchResponse.MetadataValue::idMetadata)
                .containsExactly("11329", "11330", "11331");
        assertThat(response.results().get(1).idFolder()).isEqualTo(5501L);
        openBee.server().verify(postRequestedFor(urlPathEqualTo(PATH))
                .withHeader("Authorization", equalTo("Key test-key-crm"))
                .withHeader("Content-Type", equalTo("application/json")));
    }

    /** The live Open Bee returns the hits under "items" (with offset timestamps), not the documented "results". */
    @Test
    void liveShape_itemsKey_isRead() {
        openBee.server().stubFor(post(urlPathEqualTo(PATH)).willReturn(okJson("""
                { "pagination": { "count": 1, "nbPages": 1, "itemsPerPage": 3, "page": 1 },
                  "items": [ { "idFolder": null, "idDocument": 189526, "name": "testfile",
                               "creationDate": "2026-10-30T13:12:43+00:00", "size": 72, "idFilingCategory": 11376,
                               "extension": "txt", "documentPath": "/A/B/testfile.txt",
                               "metadata": [ { "idMetadata": "11900", "value": "TEST-DMS-001" } ] } ],
                  "aggregations": [] }
                """)));

        OpenBeeSearchResponse response = openBee.client().advancedSearch(CRM, appendixC4Request());

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).idDocument()).isEqualTo(189526L);
        assertThat(response.results().get(0).creationDate()).isEqualTo("2026-10-30T13:12:43+00:00");
    }

    @Test
    void numericIdMetadata_isAccepted() {
        openBee.server().stubFor(post(urlPathEqualTo(PATH)).willReturn(okJson("""
                { "results": [ { "idDocument": 1, "metadata": [ { "idMetadata": 11329, "value": 12 } ] } ],
                  "pagination": { "count": 1, "nbPages": 1 } }
                """)));

        OpenBeeSearchResponse response = openBee.client().advancedSearch(CRM, appendixC4Request());

        assertThat(response.results().get(0).metadata().get(0).idMetadata()).isEqualTo("11329");
        assertThat(response.results().get(0).metadata().get(0).value()).isEqualTo("12");
    }

    @Test
    void serverError_isRetriedExactlyThreeTimes() {
        openBee.server().stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> openBee.client().advancedSearch(CRM, appendixC4Request()))
                .isInstanceOfSatisfying(OpenBeeException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(500);
                    assertThat(OpenBeeErrorMapper.toDms(e, OpenBeeOperation.SEARCH, true).getErrorCode().code())
                            .isEqualTo("DMS203");
                });
        openBee.server().verify(3, postRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void badRequest_isNotRetried() {
        openBee.server().stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(400)
                .withBody("{\"error\":[{\"code\":\"METADATA_NOT_EXIST\",\"message\":\"no\"}]}")));

        assertThatThrownBy(() -> openBee.client().advancedSearch(CRM, appendixC4Request()))
                .isInstanceOfSatisfying(OpenBeeException.class, e -> assertThat(e.hasCode("METADATA_NOT_EXIST")).isTrue());
        openBee.server().verify(1, postRequestedFor(urlPathEqualTo(PATH)));
    }

    private OpenBeeSearchRequest appendixC4Request() {
        return builder.build(42, List.of(
                new ValidatedMetadata(new CatalogMetadata(11329, "CLIENT_ID", "VARCHAR", MetadataTypeGroup.TEXT, true,
                        false, false, null, null, null), List.of("FIT-100234")),
                new ValidatedMetadata(new CatalogMetadata(11331, "EXPIRY_DATE", "DATE", MetadataTypeGroup.DATE, false,
                        false, false, null, null, null), List.of(LocalDate.of(2027, 12, 31)))), 1, 25);
    }
}
