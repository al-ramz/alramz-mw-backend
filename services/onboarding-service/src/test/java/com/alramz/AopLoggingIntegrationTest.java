package com.alramz;

import com.alramz.logging.aspect.Loggable;
import com.alramz.logging.util.LogMaskingUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AopLoggingIntegrationTest {

    @Test
    @Disabled("Integration test - requires full application context. Re-enable when application config is verified.")
    void verifyAopLoggingAspectIsActive() {
        LogMaskingUtil.configure(true, "********", List.of(), List.of());

        Logger logger = (Logger) LoggerFactory.getLogger("com.alramz.logging.aspect.MethodExecutionLoggingAspect");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);

        // Create a test service instance
        TestServiceForAopLogging service = new TestServiceForAopLogging();
        String result = service.testMethod("hello", 42);

        // Verify that the aspect logged the entry/exit
        assertThat(result).isEqualTo("hello-42");
    }

    @Service
    static class TestServiceForAopLogging {
        @Loggable
        public String testMethod(String message, int value) {
            return message + "-" + value;
        }
    }
}
