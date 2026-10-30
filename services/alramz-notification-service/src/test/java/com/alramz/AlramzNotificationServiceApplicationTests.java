package com.alramz;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Placeholders the base application.yml requires from the environment; Graph is never called at startup.
@SpringBootTest(properties = {
        "JWT_SECRET=context-test-secret-0123456789abcdefghijklmnop",
        "AZURE_TENANT_ID=00000000-0000-0000-0000-000000000000",
        "AZURE_CLIENT_ID=context-test",
        "AZURE_CLIENT_SECRET=context-test",
        // Middleware on H2 (Postgres mode) with the deployed test changelog: proves the changesets apply.
        "company.datasource.middleware.enabled=true",
        "company.datasource.middleware.url=jdbc:h2:mem:notification;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "company.datasource.middleware.driverClassName=org.h2.Driver",
        "company.datasource.middleware.username=sa",
        "company.datasource.middleware.plainPassword=sa",
        "company.datasource.middleware.startup-validation.enabled=false",
        "cipher.password=1234567890123456",
        "spring.liquibase.change-log=classpath:db/changelog/test/db.changelog-master.yaml"
})
class AlramzNotificationServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
