package com.alramz.service;

import com.alramz.config.DmsProperties;
import com.alramz.exception.DmsException;
import com.alramz.exception.ErrorCode;
import com.alramz.openbee.OpenBeeWireMockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** E3: the JWT subject maps to its own key by exact match; anything else is DMS109. */
class ConsumerContextResolverTest {

    private DmsProperties properties;
    private ConsumerContextResolver resolver;

    @BeforeEach
    void setUp() {
        properties = OpenBeeWireMockSupport.properties("http://localhost:65535");
        resolver = new ConsumerContextResolver(properties);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void knownSubject_resolvesToItsOwnKey() {
        authenticate("crm");

        ConsumerContext consumer = resolver.resolve();

        assertThat(consumer.consumerId()).isEqualTo("crm");
        assertThat(consumer.apiKey()).isEqualTo("test-key-crm");
    }

    @Test
    void knownSubject_isRecordedOnTheSummary() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        DmsRequestSummary summary = new DmsRequestSummary("listDmsFilingCategories", System.nanoTime());
        request.setAttribute(DmsRequestSummary.ATTRIBUTE, summary);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        authenticate("onboarding-portal");

        resolver.resolve();

        assertThat(summary.getConsumerId()).isEqualTo("onboarding-portal");
    }

    @Test
    void unknownSubject_isDms109() {
        authenticate("unknown-consumer");

        assertThatThrownBy(resolver::resolve)
                .isInstanceOfSatisfying(DmsException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DMS109));
    }

    @Test
    void caseVariant_isDms109() {
        authenticate("CRM");

        assertThatThrownBy(resolver::resolve)
                .isInstanceOfSatisfying(DmsException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DMS109));
    }

    @Test
    void blankKey_isNotUsable() {
        properties.getConsumers().get("crm").setApiKey(" ");

        assertThat(resolver.findByConsumerId("crm")).isEmpty();
        assertThat(resolver.findByConsumerId(null)).isEmpty();
        properties.getConsumers().get("crm").setApiKey(null);
        assertThat(resolver.findByConsumerId("crm")).isEmpty();
    }

    @Test
    void toString_masksTheKey() {
        ConsumerContext consumer = new ConsumerContext("crm", "test-key-crm");

        assertThat(consumer.toString()).contains("crm").doesNotContain("test-key-crm");
        assertThat(properties.getConsumers().get("crm").toString()).doesNotContain("test-key-crm");
    }

    private static void authenticate(String subject) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(subject, null,
                List.of(new SimpleGrantedAuthority("ROLE_APP_DOCUMENT_MANAGEMENT_SERVICE_USER"))));
    }
}
