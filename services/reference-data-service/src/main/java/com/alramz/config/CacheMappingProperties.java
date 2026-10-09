package com.alramz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "alramz.cache")
public class CacheMappingProperties {

    private Map<String, CacheMapping> mappings;

    public Map<String, CacheMapping> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, CacheMapping> mappings) {
        this.mappings = mappings;
    }

    public static class CacheMapping {
        private String table;
        private String cacheKey;
        private String reloadStrategy;
        private String ttl;
        private boolean enabled = true;

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getCacheKey() {
            return cacheKey;
        }

        public void setCacheKey(String cacheKey) {
            this.cacheKey = cacheKey;
        }

        public String getReloadStrategy() {
            return reloadStrategy;
        }

        public void setReloadStrategy(String reloadStrategy) {
            this.reloadStrategy = reloadStrategy;
        }

        public String getTtl() {
            return ttl;
        }

        public void setTtl(String ttl) {
            this.ttl = ttl;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
