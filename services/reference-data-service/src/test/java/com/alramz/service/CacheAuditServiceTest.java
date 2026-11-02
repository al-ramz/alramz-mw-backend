package com.alramz.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheAuditServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @InjectMocks
    private CacheAuditService service;

    @Test
    void logAuditAsync_shouldInsertAuditRowWithAllParams() {
        service.logAuditAsync("countries", "ref:countries", "FLUSH", "API", "manual");

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(startsWith("INSERT INTO cache_audit_log"), captor.capture());
        MapSqlParameterSource p = captor.getValue();
        assertThat(p.getValue("mappingName")).isEqualTo("countries");
        assertThat(p.getValue("cacheKey")).isEqualTo("ref:countries");
        assertThat(p.getValue("eventType")).isEqualTo("FLUSH");
        assertThat(p.getValue("triggeredBy")).isEqualTo("API");
        assertThat(p.getValue("triggerSource")).isEqualTo("manual");
        assertThat(p.getValue("details")).isNull();
    }

    @Test
    void logAuditAsync_shouldSwallowDatabaseError() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatNoException().isThrownBy(() -> service.logAuditAsync("m", "k", "E", "T", "S"));
    }
}
