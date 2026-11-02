package com.alramz.service;

import com.alramz.config.CompanyRedisProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCacheServiceTest {

    private static final String MAPPING = "countries";
    private static final String CACHE_KEY = "ref:countries";
    private static final String CACHED_JSON = "[{\"code\":\"AE\"},{\"code\":\"SA\"}]";

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private NamedParameterJdbcTemplate jdbc;
    @Mock
    private CacheAuditService auditService;

    private CompanyRedisProperties.Cache.CacheMapping mapping;
    private RedisCacheService service;

    @BeforeEach
    void setUp() {
        mapping = new CompanyRedisProperties.Cache.CacheMapping();
        mapping.setTable("countries");
        mapping.setCacheKey(CACHE_KEY);
        mapping.setTtl("10M");
        Map<String, CompanyRedisProperties.Cache.CacheMapping> mappings = new HashMap<>();
        mappings.put(MAPPING, mapping);
        CompanyRedisProperties props = new CompanyRedisProperties();
        props.getCache().setMappings(mappings);
        service = new RedisCacheService(redisTemplate, new ObjectMapper(), props, jdbc, auditService);
    }

    @SuppressWarnings("unchecked")
    private void stubDbRows(List<Map<String, Object>> rows) {
        when(jdbc.query(startsWith("SELECT * FROM"), any(RowMapper.class))).thenReturn(rows);
    }

    @Test
    void getAllEntries_shouldReturnCachedEntriesWhenPresent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(CACHED_JSON);

        List<Map<String, Object>> result = service.getAllEntries(MAPPING);

        assertThat(result).extracting(m -> m.get("code")).containsExactly("AE", "SA");
        verify(jdbc, never()).query(anyString(), any(RowMapper.class));
    }

    @Test
    void getEntry_shouldDelegateToGetAllEntries() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(CACHED_JSON);

        assertThat(service.getEntry(MAPPING, "AE")).hasSize(2);
    }

    @Test
    void getAllEntries_shouldLoadFromDbAndRepopulateWithTtlOnMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        stubDbRows(List.of(Map.of("code", "AE")));

        List<Map<String, Object>> result = service.getAllEntries(MAPPING);

        assertThat(result).containsExactly(Map.of("code", "AE"));
        verify(valueOps).set(CACHE_KEY, "[{\"code\":\"AE\"}]", Duration.ofMinutes(10));
        verify(auditService).logAuditAsync(eq(MAPPING), eq(CACHE_KEY), eq("EVICTED"), eq("READ"), anyString());
    }

    @Test
    void getAllEntries_shouldRepopulateWithoutTtlWhenTtlIsZero() {
        mapping.setTtl("0");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        stubDbRows(List.of());

        assertThat(service.getAllEntries(MAPPING)).isEmpty();
        verify(valueOps).set(CACHE_KEY, "[]");
    }

    @Test
    void getAllEntries_shouldFallBackToDbWhenRedisReadFailsAndSwallowRepopulateFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenThrow(new RedisConnectionFailureException("down"));
        doThrow(new RedisConnectionFailureException("down")).when(valueOps).set(anyString(), anyString(), any(Duration.class));
        stubDbRows(List.of(Map.of("code", "AE")));

        assertThat(service.getAllEntries(MAPPING)).hasSize(1);
        verify(auditService, never()).logAuditAsync(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void getAllEntries_shouldPropagateDbFailureOnMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> service.getAllEntries(MAPPING))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void getAllEntries_shouldFallBackToDbWhenCachedJsonIsCorrupt() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn("not-json");
        stubDbRows(List.of(Map.of("code", "AE")));

        assertThat(service.getAllEntries(MAPPING)).hasSize(1);
    }

    @Test
    void getAllEntries_shouldRejectUnknownMapping() {
        assertThatThrownBy(() -> service.getAllEntries("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown cache mapping: nope");
    }

    @Test
    void getAllEntries_shouldRejectDisabledMapping() {
        mapping.setEnabled(false);

        assertThatThrownBy(() -> service.getAllEntries(MAPPING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadAllFromDatabase_rowMapperShouldMapEveryColumnByLabel() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        ArgumentCaptor<RowMapper<Map<String, Object>>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbc.query(eq("SELECT * FROM countries"), captor.capture())).thenReturn(List.of());
        service.getAllEntries(MAPPING);

        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData md = mock(ResultSetMetaData.class);
        when(rs.getMetaData()).thenReturn(md);
        when(md.getColumnCount()).thenReturn(2);
        when(md.getColumnLabel(1)).thenReturn("code");
        when(md.getColumnLabel(2)).thenReturn("name");
        when(rs.getObject(1)).thenReturn("AE");
        when(rs.getObject(2)).thenReturn("UAE");

        Map<String, Object> row = captor.getValue().mapRow(rs, 0);

        assertThat(row).containsExactly(Map.entry("code", "AE"), Map.entry("name", "UAE"));
    }

    @Test
    void flushCache_shouldDeleteKeyAndAudit() {
        when(redisTemplate.delete(CACHE_KEY)).thenReturn(true);

        assertThat(service.flushCache(MAPPING)).isEqualTo("Cache flushed successfully");
        verify(auditService).logAuditAsync(eq(MAPPING), eq(CACHE_KEY), eq("FLUSH"), anyString(), anyString());
    }

    @Test
    void flushCache_shouldReportFailureWhenRedisThrows() {
        when(redisTemplate.delete(CACHE_KEY)).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(service.flushCache(MAPPING)).isEqualTo("Cache flush failed: down");
    }

    @Test
    void reloadGlobalConfig_shouldWriteStagingKeyRenameAndReleaseCompleted() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        stubDbRows(List.of(Map.of("code", "AE"), Map.of("code", "SA")));

        String result = service.reloadGlobalConfig(MAPPING);

        assertThat(result).isEqualTo("Cache reloaded successfully with 2 entries");
        verify(valueOps).set(startsWith(CACHE_KEY + ":staging:"), anyString(), eq(Duration.ofMinutes(10)));
        verify(redisTemplate).rename(startsWith(CACHE_KEY + ":staging:"), eq(CACHE_KEY));
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(startsWith("UPDATE application_workflow_locks"), params.capture());
        assertThat(params.getValue().getValue("status")).isEqualTo("COMPLETED");
        verify(auditService).logAuditAsync(MAPPING, CACHE_KEY, "RELOAD", "API", "Reloaded 2 entries");
    }

    @Test
    void reloadGlobalConfig_shouldSetStagingWithoutTtlWhenTtlInvalid() {
        mapping.setTtl("garbage");
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        stubDbRows(List.of());

        assertThat(service.reloadGlobalConfig(MAPPING)).contains("0 entries");
        verify(valueOps).set(startsWith(CACHE_KEY + ":staging:"), eq("[]"));
    }

    @Test
    void reloadGlobalConfig_shouldReturnInProgressWhenLockNotAcquired() {
        when(jdbc.update(startsWith("DELETE"), any(MapSqlParameterSource.class))).thenReturn(0);
        when(jdbc.update(startsWith("INSERT"), any(MapSqlParameterSource.class))).thenReturn(0);

        assertThat(service.reloadGlobalConfig(MAPPING)).isEqualTo("Reload already in progress for mapping: countries");
        verify(jdbc, never()).query(anyString(), any(RowMapper.class));
    }

    @Test
    void reloadGlobalConfig_shouldTreatLockErrorAsNotAcquiredAndTolerateCleanupError() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThat(service.reloadGlobalConfig(MAPPING)).startsWith("Reload already in progress");
    }

    @Test
    void reloadGlobalConfig_shouldDeleteStagingKeyAndReleaseFailedWhenRenameFails() {
        when(jdbc.update(startsWith("DELETE"), any(MapSqlParameterSource.class))).thenReturn(0);
        when(jdbc.update(startsWith("INSERT"), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbc.update(startsWith("UPDATE"), any(MapSqlParameterSource.class)))
                .thenThrow(new DataAccessResourceFailureException("release failed"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        stubDbRows(List.of());
        doThrow(new RedisConnectionFailureException("rename failed")).when(redisTemplate).rename(anyString(), anyString());

        String result = service.reloadGlobalConfig(MAPPING);

        assertThat(result).isEqualTo("Cache reload failed: rename failed");
        verify(redisTemplate).delete(startsWith(CACHE_KEY + ":staging:"));
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(startsWith("UPDATE"), params.capture());
        assertThat(params.getValue().getValue("status")).isEqualTo("FAILED");
        verify(auditService, never()).logAuditAsync(anyString(), anyString(), eq("RELOAD"), anyString(), anyString());
    }

    @Test
    void getCacheStatus_shouldReportEntriesAndSizeWhenKeyExists() {
        mapping.setTtl("PT1H");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("__health_check__")).thenReturn(null);
        when(redisTemplate.hasKey(CACHE_KEY)).thenReturn(true);
        when(valueOps.get(CACHE_KEY)).thenReturn(CACHED_JSON);

        Map<String, Object> status = service.getCacheStatus(MAPPING);

        assertThat(status)
                .containsEntry("cacheKey", CACHE_KEY)
                .containsEntry("exists", true)
                .containsEntry("entryCount", 2L)
                .containsEntry("sourceTable", "countries")
                .containsEntry("redisAvailable", true)
                .containsEntry("ttl", 3600L)
                .containsEntry("cacheSizeBytes", (long) CACHED_JSON.length());
    }

    @Test
    void getCacheStatus_shouldReportMissingKeyWithZeroTtl() {
        mapping.setTtl(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.hasKey(CACHE_KEY)).thenReturn(false);

        Map<String, Object> status = service.getCacheStatus(MAPPING);

        assertThat(status)
                .containsEntry("exists", false)
                .containsEntry("entryCount", 0L)
                .containsEntry("ttl", 0L)
                .containsEntry("cacheSizeBytes", null);
    }

    @Test
    void getCacheStatus_shouldReportZeroTtlForInvalidValueAndSurviveHasKeyError() {
        mapping.setTtl("bogus");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.hasKey(CACHE_KEY)).thenThrow(new RedisConnectionFailureException("x"));

        Map<String, Object> status = service.getCacheStatus(MAPPING);

        assertThat(status).containsEntry("exists", false).containsEntry("ttl", 0L);
    }

    @Test
    void getCacheStatus_shouldSkipRedisLookupsWhenRedisUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("__health_check__")).thenThrow(new RedisConnectionFailureException("down"));

        Map<String, Object> status = service.getCacheStatus(MAPPING);

        assertThat(status).containsEntry("redisAvailable", false).containsEntry("exists", false);
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    void checkDeepHealth_shouldBeUpWhenDbAndRedisReachable() {
        when(jdbc.queryForObject(eq("SELECT 1"), anyMap(), eq(Integer.class))).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        Map<String, Object> health = service.checkDeepHealth();

        assertThat(health)
                .containsEntry("databaseReachable", true)
                .containsEntry("redisReachable", true)
                .containsEntry("overallStatus", "UP")
                .containsEntry("details", Map.of("database", "connected", "redis", "connected"))
                .containsKey("timestamp");
    }

    @Test
    void checkDeepHealth_shouldBeDownWhenDbAndRedisUnreachable() {
        when(jdbc.queryForObject(contains("SELECT 1"), anyMap(), eq(Integer.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        Map<String, Object> health = service.checkDeepHealth();

        assertThat(health)
                .containsEntry("overallStatus", "DOWN")
                .containsEntry("details", Map.of("database", "unreachable", "redis", "unreachable"));
    }
}
