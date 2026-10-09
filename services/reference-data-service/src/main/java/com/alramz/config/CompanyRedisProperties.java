package com.alramz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "company.redis")
public class CompanyRedisProperties {

    private Deployment deployment = new Deployment();
    private Connection connection = new Connection();
    private Cache cache = new Cache();

    public Deployment getDeployment() {
        return deployment;
    }

    public void setDeployment(Deployment deployment) {
        this.deployment = deployment;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public static class Deployment {
        private String mode = "local";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }
    }

    public static class Connection {
        private String host = "localhost";
        private int port = 6379;
        private String password = "";
        private Ssl ssl = new Ssl();
        private String timeout = "2s";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public Ssl getSsl() {
            return ssl;
        }

        public void setSsl(Ssl ssl) {
            this.ssl = ssl;
        }

        public String getTimeout() {
            return timeout;
        }

        public void setTimeout(String timeout) {
            this.timeout = timeout;
        }

        public static class Ssl {
            private boolean enabled = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }
    }

    public static class Cache {
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
}
