package com.alramz.service;

import com.alramz.config.CompanyRedisProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);
    private static final String INSTANCE_ID = UUID.randomUUID().toString();

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CompanyRedisProperties companyRedisProperties;
    private final NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate;
    private final CacheAuditService cacheAuditService;

    public RedisCacheService(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            CompanyRedisProperties companyRedisProperties,
            NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate,
            CacheAuditService cacheAuditService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.companyRedisProperties = companyRedisProperties;
        this.middlewareNamedParameterJdbcTemplate = middlewareNamedParameterJdbcTemplate;
        this.cacheAuditService = cacheAuditService;
    }

    public List<Map<String, Object>> getAllEntries(String mappingName) {
        CompanyRedisProperties.Cache.CacheMapping mapping = resolveMapping(mappingName);
        String cacheKey = mapping.getCacheKey();

        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json != null) {
                return deserializeList(json);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable or error reading key {}: {}", cacheKey, e.getMessage());
        }

        return loadFromDatabaseAndRepopulate(mappingName, mapping, "READ");
    }

    public List<Map<String, Object>> getEntry(String mappingName, String key) {
        return getAllEntries(mappingName);
    }

    public String flushCache(String mappingName) {
        CompanyRedisProperties.Cache.CacheMapping mapping = resolveMapping(mappingName);
        String cacheKey = mapping.getCacheKey();

        try {
            Boolean deleted = redisTemplate.delete(cacheKey);
            cacheAuditService.logAuditAsync(mappingName, cacheKey, "FLUSH", "API", "Cache flushed via API");
            return "Cache flushed successfully";
        } catch (Exception e) {
            log.error("Failed to flush cache for key {}: {}", cacheKey, e.getMessage(), e);
            return "Cache flush failed: " + e.getMessage();
        }
    }

    public String reloadGlobalConfig(String mappingName) {
        CompanyRedisProperties.Cache.CacheMapping mapping = resolveMapping(mappingName);
        String cacheKey = mapping.getCacheKey();

        cleanStaleLocks(mappingName);

        if (!tryAcquireLock(mappingName, Duration.ofMinutes(1))) {
            return "Reload already in progress for mapping: " + mappingName;
        }

        try {
            List<Map<String, Object>> settings = loadAllFromDatabase(mappingName);
            String json = serializeList(settings);

            String stagingKey = cacheKey + ":staging:" + System.currentTimeMillis();

            try {
                Duration ttl = parseTtl(mapping.getTtl());
                if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
                    redisTemplate.opsForValue().set(stagingKey, json, ttl);
                } else {
                    redisTemplate.opsForValue().set(stagingKey, json);
                }
                redisTemplate.rename(stagingKey, cacheKey);
            } catch (Exception e) {
                redisTemplate.delete(stagingKey);
                throw e;
            }

            releaseLock(mappingName, "COMPLETED");
            cacheAuditService.logAuditAsync(mappingName, cacheKey, "RELOAD", "API",
                    "Reloaded " + settings.size() + " entries");

            return "Cache reloaded successfully with " + settings.size() + " entries";
        } catch (Exception e) {
            releaseLock(mappingName, "FAILED");
            log.error("Failed to reload cache for mapping {}: {}", mappingName, e.getMessage(), e);
            return "Cache reload failed: " + e.getMessage();
        }
    }

    public Map<String, Object> getCacheStatus(String mappingName) {
        CompanyRedisProperties.Cache.CacheMapping mapping = resolveMapping(mappingName);
        String cacheKey = mapping.getCacheKey();

        boolean redisAvailable = isRedisAvailable();
        boolean exists = false;
        Long entryCount = 0L;
        LocalDateTime lastReloaded = null;
        Long cacheSizeBytes = null;

        if (redisAvailable) {
            try {
                exists = Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey));
                if (exists) {
                    String json = redisTemplate.opsForValue().get(cacheKey);
                    if (json != null) {
                        List<Map<String, Object>> settings = deserializeList(json);
                        entryCount = (long) settings.size();
                        cacheSizeBytes = (long) json.getBytes().length;
                    }
                }
            } catch (Exception e) {
                log.warn("Error checking cache status for key {}: {}", cacheKey, e.getMessage());
            }
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("cacheKey", cacheKey);
        status.put("exists", exists);
        status.put("entryCount", entryCount);
        status.put("sourceTable", mapping.getTable());
        status.put("redisAvailable", redisAvailable);
        status.put("ttl", parseTtlSeconds(mapping.getTtl()));
        status.put("cacheSizeBytes", cacheSizeBytes);
        return status;
    }

    public Map<String, Object> checkDeepHealth() {
        boolean dbAvailable = isDatabaseAvailable();
        boolean redisAvailable = isRedisAvailable();
        String overallStatus = (dbAvailable && redisAvailable) ? "UP" : "DOWN";

        Map<String, Object> details = Map.of(
                "database", dbAvailable ? "connected" : "unreachable",
                "redis", redisAvailable ? "connected" : "unreachable"
        );

        return Map.of(
                "databaseReachable", dbAvailable,
                "redisReachable", redisAvailable,
                "overallStatus", overallStatus,
                "timestamp", LocalDateTime.now().toString(),
                "details", details
        );
    }

    private List<Map<String, Object>> loadFromDatabaseAndRepopulate(String mappingName,
            CompanyRedisProperties.Cache.CacheMapping mapping, String triggerSource) {
        try {
            List<Map<String, Object>> settings = loadAllFromDatabase(mappingName);
            String json = serializeList(settings);
            String cacheKey = mapping.getCacheKey();

            try {
                Duration ttl = parseTtl(mapping.getTtl());
                if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
                    redisTemplate.opsForValue().set(cacheKey, json, ttl);
                } else {
                    redisTemplate.opsForValue().set(cacheKey, json);
                }
                cacheAuditService.logAuditAsync(mappingName, cacheKey, "EVICTED", triggerSource,
                        "Auto-repopulated from DB after miss");
            } catch (Exception e) {
                log.warn("Failed to repopulate cache for key {}: {}", cacheKey, e.getMessage());
            }

            return settings;
        } catch (Exception e) {
            log.error("Database fallback failed for mapping {}: {}", mappingName, e.getMessage(), e);
            throw e;
        }
    }

    private List<Map<String, Object>> loadAllFromDatabase(String mappingName) {
        CompanyRedisProperties.Cache.CacheMapping mapping = resolveMapping(mappingName);
        String tableName = mapping.getTable();
        String sql = "SELECT * FROM " + tableName;
        return middlewareNamedParameterJdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
            }
            return row;
        });
    }

    private CompanyRedisProperties.Cache.CacheMapping resolveMapping(String mappingName) {
        CompanyRedisProperties.Cache.CacheMapping mapping = companyRedisProperties.getCache().getMappings().get(mappingName);
        if (mapping == null) {
            throw new IllegalArgumentException("Unknown cache mapping: " + mappingName);
        }
        if (!mapping.isEnabled()) {
            throw new IllegalStateException("Cache mapping is disabled: " + mappingName);
        }
        return mapping;
    }

    private boolean tryAcquireLock(String mappingName, Duration leaseDuration) {
        String sql = "INSERT INTO application_workflow_locks (mapping_name, locked_by, status, expires_at) " +
                "VALUES (:mappingName, :lockedBy, 'IN_PROGRESS', :expiresAt) " +
                "ON CONFLICT (mapping_name) " +
                "DO UPDATE SET locked_at = EXCLUDED.locked_at, locked_by = EXCLUDED.locked_by, " +
                "status = EXCLUDED.status, completed_at = NULL, expires_at = EXCLUDED.expires_at " +
                "WHERE application_workflow_locks.expires_at < NOW() AND application_workflow_locks.status = 'IN_PROGRESS'";

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("mappingName", mappingName);
        params.addValue("lockedBy", INSTANCE_ID);
        params.addValue("expiresAt", LocalDateTime.now().plus(leaseDuration));

        try {
            int updated = middlewareNamedParameterJdbcTemplate.update(sql, params);
            return updated > 0;
        } catch (Exception e) {
            log.error("Failed to acquire reload lock for mapping {}: {}", mappingName, e.getMessage(), e);
            return false;
        }
    }

    private void releaseLock(String mappingName, String status) {
        String sql = "UPDATE application_workflow_locks SET status = :status, completed_at = NOW() " +
                "WHERE mapping_name = :mappingName AND locked_by = :lockedBy";

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("mappingName", mappingName);
        params.addValue("lockedBy", INSTANCE_ID);
        params.addValue("status", status);

        try {
            middlewareNamedParameterJdbcTemplate.update(sql, params);
        } catch (Exception e) {
            log.error("Failed to release reload lock for mapping {}: {}", mappingName, e.getMessage(), e);
        }
    }

    private void cleanStaleLocks(String mappingName) {
        String sql = "DELETE FROM application_workflow_locks WHERE mapping_name = :mappingName AND expires_at < NOW()";

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("mappingName", mappingName);

        try {
            middlewareNamedParameterJdbcTemplate.update(sql, params);
        } catch (Exception e) {
            log.warn("Failed to clean stale locks for mapping {}: {}", mappingName, e.getMessage(), e);
        }
    }

    private boolean isRedisAvailable() {
        try {
            redisTemplate.opsForValue().get("__health_check__");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isDatabaseAvailable() {
        try {
            middlewareNamedParameterJdbcTemplate.queryForObject("SELECT 1", Map.of(), Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String serializeList(List<Map<String, Object>> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize cache data", e);
        }
    }

    private List<Map<String, Object>> deserializeList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize cached data", e);
        }
    }

    private Duration parseTtl(String ttl) {
        if (ttl == null || ttl.isBlank() || "0".equals(ttl.trim())) {
            return null;
        }
        try {
            return Duration.parse(ttl.startsWith("PT") ? ttl : "PT" + ttl);
        } catch (Exception e) {
            log.warn("Invalid TTL value: {}, disabling TTL", ttl);
            return null;
        }
    }

    private long parseTtlSeconds(String ttl) {
        if (ttl == null || ttl.isBlank() || "0".equals(ttl.trim())) {
            return 0;
        }
        try {
            Duration d = parseTtl(ttl);
            return d != null ? d.getSeconds() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
