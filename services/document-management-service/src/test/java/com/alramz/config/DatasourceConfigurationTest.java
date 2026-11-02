package com.alramz.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The starter's JWT repositories run in a transaction named {@code transactionManager}; without
 * {@link DatasourceConfiguration} register/login fail with NoSuchBeanDefinitionException (500).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DatasourceConfigurationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    @Qualifier("middlewareTransactionManager")
    private PlatformTransactionManager middlewareTransactionManager;

    @Test
    void primaryTransactionManager_isTheMiddlewareOne() {
        assertThat(transactionManager).isSameAs(middlewareTransactionManager);
    }

    @Test
    void registerThenLogin_returnsAnAccessToken() throws Exception {
        RestClient client = RestClient.create("http://localhost:" + port);

        ResponseEntity<String> register = client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"authType":"JWT","consumer":"crm","email":"crm@example.com","consumerPassword":"password123",
                         "roles":["APP_DOCUMENT_MANAGEMENT_SERVICE_USER"],"application":"document-management-service",
                         "environment":"test"}""")
                .retrieve().toEntity(String.class);
        assertThat(register.getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> login = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"consumer\":\"crm\",\"consumerPassword\":\"password123\"}")
                .retrieve().toEntity(String.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        JsonNode token = JSON.readTree(login.getBody()).path("accessToken");
        assertThat(token.asText()).isNotBlank();
    }
}
