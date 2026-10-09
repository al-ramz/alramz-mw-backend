package com.alramz.audit;

import com.alramz.controllers.AuditProbeController;
import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.util.MDCUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiAuditLogFilterTest {

    private final ApiAuditLogService auditLogService = mock(ApiAuditLogService.class);

    @AfterEach
    void clearMdc() {
        MDCUtil.clear();
    }

    private MockHttpServletResponse handle() throws Exception {
        return handle(new MockHttpServletRequest("POST", "/api/v1/community/posts"));
    }

    private MockHttpServletResponse handle(MockHttpServletRequest request) throws Exception {
        when(auditLogService.getMasker()).thenReturn(new SensitiveDataMasker(new ObjectMapper(), true));
        LoggingProperties properties = new LoggingProperties();
        properties.getDatabaseLogging().setEnabled(true);
        ApiAuditLogFilter filter = new ApiAuditLogFilter(properties,
                new MockEnvironment().withProperty("spring.application.name", "test-service"),
                auditLogService, new ObjectMapper());
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private ApiAuditLog recordedEntry() {
        ArgumentCaptor<ApiAuditLog> entry = ArgumentCaptor.forClass(ApiAuditLog.class);
        verify(auditLogService).log(entry.capture());
        return entry.getValue();
    }

    @Test
    void completesTheRequestWhenTheCorrelationIdIsNotAUuid() throws Exception {
        MDCUtil.putCorrelationId("not-a-uuid");

        assertThat(handle().getStatus()).isEqualTo(200);
        assertThat(recordedEntry().correlationId()).isNotNull();
    }

    @Test
    void keepsAValidCorrelationId() throws Exception {
        MDCUtil.putCorrelationId("2afe4a42-44f7-4e12-aae7-df0e42d4d757");

        handle();

        assertThat(recordedEntry().correlationId().toString()).isEqualTo("2afe4a42-44f7-4e12-aae7-df0e42d4d757");
    }

    @Test
    void recordsTheControllerMethodThatHandledTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/probe");
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                new HandlerMethod(new AuditProbeController(), AuditProbeController.class.getMethod("create", String.class)));

        handle(request);

        ApiAuditLog entry = recordedEntry();
        assertThat(entry.controllerName()).isEqualTo("AuditProbeController.create");
        assertThat(entry.apiEndpoint()).isEqualTo("/api/v1/probe");
        assertThat(entry.method()).isEqualTo("POST");
        assertThat(entry.statusCode()).isEqualTo(200);
    }
}
