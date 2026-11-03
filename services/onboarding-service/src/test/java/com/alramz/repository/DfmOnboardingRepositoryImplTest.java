package com.alramz.repository;

import com.alramz.config.JacksonConfig;
import com.alramz.model.EmpRelated;
import com.alramz.model.EmpRelateds;
import com.alramz.model.OnboardingRequest;
import com.alramz.utils.SqlQueriesManager;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.io.IOException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DfmOnboardingRepositoryImplTest {

    private static final String SQL = "INSERT INTO DFM_ONBOARDING VALUES (...)";

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private SqlQueriesManager sqlQueriesManager;

    @SuppressWarnings("unchecked")
    private Map<String, Object> insertAndCaptureParams(DfmOnboardingRepositoryImpl repository, OnboardingRequest request)
            throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig("dfm.onboarding.insert")).thenReturn(SQL);
        repository.insert(request, "MEMBER-REF-1");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.eq(SQL), captor.capture());
        return captor.getValue();
    }

    @Test
    void insert_shouldMapRequestFieldsAndDefaultsIntoNamedParameters() throws IOException {
        DfmOnboardingRepositoryImpl repository =
                new DfmOnboardingRepositoryImpl(jdbcTemplate, new JacksonConfig().objectMapper(), sqlQueriesManager);
        OnboardingRequest request = new OnboardingRequest();
        request.setRequestId("REQ-1");
        request.setCustNin("EGA0000000");
        request.setEidDob(LocalDate.of(1990, 5, 17));
        EmpRelated related = new EmpRelated();
        EmpRelateds relateds = new EmpRelateds();
        relateds.setEmpRelated(List.of(related));
        request.setEmpRelateds(relateds);
        request.setKycMatchDetails(Map.of("score", "99"));

        Map<String, Object> params = insertAndCaptureParams(repository, request);

        assertThat(params)
                .containsEntry("requestId", "REQ-1")
                .containsEntry("custNin", "EGA0000000")
                .containsEntry("eidDob", Date.valueOf(LocalDate.of(1990, 5, 17)))
                .containsEntry("memberReferenceNumber", "MEMBER-REF-1")
                .containsEntry("accountStatus", "NEW")
                .containsEntry("cashTradingNumber", null)
                .containsEntry("marginTradingNumber", null)
                .containsEntry("userCode", null)
                .containsEntry("fitNumber", null)
                .containsEntry("kycMatchDetailsJson", "{\"score\":\"99\"}");
        assertThat((String) params.get("empRelatedsJson")).startsWith("{\"emp_related\":[");
        assertThat(params.get("dfmRequestTime")).isInstanceOf(Timestamp.class);
    }

    @Test
    void insert_shouldPassNullForAbsentDatesAndJsonObjects() throws IOException {
        DfmOnboardingRepositoryImpl repository =
                new DfmOnboardingRepositoryImpl(jdbcTemplate, new JacksonConfig().objectMapper(), sqlQueriesManager);
        OnboardingRequest request = new OnboardingRequest();
        request.setKycMatchDetails(null);

        Map<String, Object> params = insertAndCaptureParams(repository, request);

        assertThat(params)
                .containsEntry("eidDob", null)
                .containsEntry("eidExpirydate", null)
                .containsEntry("ppDob", null)
                .containsEntry("empRelatedsJson", null)
                .containsEntry("csrJson", null)
                .containsEntry("kycMatchDetailsJson", null);
    }

    @Test
    void insert_shouldStoreNullJsonWhenSerializationFails() throws IOException {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new JsonMappingException(null, "boom"));
        DfmOnboardingRepositoryImpl repository =
                new DfmOnboardingRepositoryImpl(jdbcTemplate, failingMapper, sqlQueriesManager);
        OnboardingRequest request = new OnboardingRequest();
        request.setKycMatchDetails(Map.of("score", "99"));

        Map<String, Object> params = insertAndCaptureParams(repository, request);

        assertThat(params).containsEntry("kycMatchDetailsJson", null);
    }

    @Test
    void insert_shouldThrowIllegalStateWhenQueryCannotBeLoaded() throws IOException {
        DfmOnboardingRepositoryImpl repository =
                new DfmOnboardingRepositoryImpl(jdbcTemplate, new JacksonConfig().objectMapper(), sqlQueriesManager);
        when(sqlQueriesManager.getSQLQueryFromConfig(anyString())).thenThrow(new IOException("missing file"));

        OnboardingRequest input = new OnboardingRequest();

        assertThatThrownBy(() -> repository.insert(input, "MEMBER-REF-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to load onboarding insert query")
                .hasCauseInstanceOf(IOException.class);
        verify(jdbcTemplate, never()).update(anyString(), anyMap());
    }
}
