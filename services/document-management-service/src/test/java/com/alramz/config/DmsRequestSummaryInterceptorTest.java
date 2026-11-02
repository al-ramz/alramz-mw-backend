package com.alramz.config;

import com.alramz.logging.util.MDCUtil;
import com.alramz.service.DmsRequestSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(OutputCaptureExtension.class)
class DmsRequestSummaryInterceptorTest {

    private final DmsRequestSummaryInterceptor interceptor = new DmsRequestSummaryInterceptor();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void successfulSave_logsExactlyOneLineWithIdsAndExtensionOnly(CapturedOutput output) throws Exception {
        MDCUtil.putCorrelationId("3f2c1a9e-7b4d-4c1e-9a6f-2d8e5b7c0a11");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/dms/documents");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = new HandlerMethod(new Probe(), Probe.class.getDeclaredMethod("saveDmsDocument"));

        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
        DmsRequestSummary summary = (DmsRequestSummary) request.getAttribute(DmsRequestSummary.ATTRIBUTE);
        summary.setConsumerId("onboarding-portal");
        summary.setFilingCategoryId(42L);
        summary.setDocumentId(88213L);
        summary.setOpenBeeStatus(201);
        summary.setExtension("pdf");
        response.setStatus(201);
        interceptor.afterCompletion(request, response, handler, null);

        // the starter logs through an AsyncAppender: wait for the line before asserting on it
        await().atMost(Duration.ofSeconds(5)).until(() -> output.getOut().contains("DMS request"));
        String[] lines = Arrays.stream(output.getOut().split("\\R")).filter(l -> l.contains("DMS request")).toArray(String[]::new);
        assertThat(lines).hasSize(1);
        assertThat(lines[0]).contains("correlationId=3f2c1a9e-7b4d-4c1e-9a6f-2d8e5b7c0a11", "consumerId=onboarding-portal",
                "operation=saveDmsDocument", "filingCategoryId=42", "documentId=88213", "openBeeStatus=201",
                "httpStatus=201", "dmsCode=null", "durationMs=", "extension=pdf");
    }

    @Test
    void failedRequest_logsTheDmsCode(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/dms/documents/search");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        ((DmsRequestSummary) request.getAttribute(DmsRequestSummary.ATTRIBUTE)).setDmsCode("DMS105");
        response.setStatus(400);
        interceptor.afterCompletion(request, response, new Object(), null);
        await().atMost(Duration.ofSeconds(5)).until(() -> output.getOut().contains("DMS request"));

        assertThat(output).contains("operation=unknown").contains("httpStatus=400").contains("dmsCode=DMS105");
    }

    @Test
    void withoutASummary_logsNothing(CapturedOutput output) {
        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

        assertThat(output).doesNotContain("DMS request");
    }

    @Test
    void webMvcConfig_registersTheInterceptorForDmsPathsOnly() {
        ExposedRegistry registry = new ExposedRegistry();

        new DmsWebMvcConfig().addInterceptors(registry);

        assertThat(registry.interceptors()).singleElement().isInstanceOfSatisfying(MappedInterceptor.class, mapped -> {
            assertThat(mapped.getInterceptor()).isInstanceOf(DmsRequestSummaryInterceptor.class);
            assertThat(mapped.matches(parsed("/api/v1/dms/documents/1/content"))).isTrue();
            assertThat(mapped.matches(parsed("/api/v1/dms/filing-categories"))).isTrue();
            assertThat(mapped.matches(parsed("/api/v1/info"))).isFalse();
        });
    }

    private static MockHttpServletRequest parsed(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        ServletRequestPathUtils.parseAndCache(request);
        return request;
    }

    static class ExposedRegistry extends InterceptorRegistry {
        List<Object> interceptors() {
            return getInterceptors();
        }
    }

    static class Probe {
        @SuppressWarnings("unused")
        void saveDmsDocument() {
            // probe only
        }
    }
}
