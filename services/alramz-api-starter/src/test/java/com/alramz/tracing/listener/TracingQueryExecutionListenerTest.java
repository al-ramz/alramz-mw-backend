package com.alramz.tracing.listener;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TracingQueryExecutionListenerTest {

    @Test
    void operationIsFirstSqlKeywordAcrossAnyWhitespace() {
        assertThat(TracingQueryExecutionListener.extractOperation("\n        SELECT\n            ID FROM T")).isEqualTo("SELECT");
        assertThat(TracingQueryExecutionListener.extractOperation("insert\tinto t values (1)")).isEqualTo("INSERT");
        assertThat(TracingQueryExecutionListener.extractOperation("COMMIT")).isEqualTo("COMMIT");
        assertThat(TracingQueryExecutionListener.extractOperation("   ")).isNull();
    }
}
