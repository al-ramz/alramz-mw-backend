package com.alramz.config;

import com.alramz.service.FilingCategoryCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(OutputCaptureExtension.class)
class DmsCacheConfigTest {

    /** An Error escaping a scheduled run would cancel every later run: it is logged (class name only) instead. */
    @Test
    void refreshSafely_neverLetsAnythingEscape(CapturedOutput output) {
        FilingCategoryCatalog catalog = mock(FilingCategoryCatalog.class);
        doThrow(new OutOfMemoryError("test-key-onboarding")).when(catalog).refreshAhead();

        assertThatCode(() -> DmsCacheConfig.refreshSafely(catalog)).doesNotThrowAnyException();

        verify(catalog).refreshAhead();
        await().atMost(Duration.ofSeconds(5)).until(() -> output.getOut().contains("Background refresh of filing categories failed"));
        assertThat(output.getOut()).contains("java.lang.OutOfMemoryError").doesNotContain("test-key-onboarding");
    }

    @Test
    void executors_useDaemonThreadsWithTheirPrefix() throws Exception {
        Thread thread = DmsCacheConfig.daemonThreads("dms-test-").newThread(() -> { });

        assertThat(thread.isDaemon()).isTrue();
        assertThat(thread.getName()).isEqualTo("dms-test-1");
    }
}
