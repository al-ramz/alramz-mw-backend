package com.alramz.logging.util;

import com.alramz.logging.config.LoggingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MethodExecutionLoggingHelperTest {

    private LoggingProperties properties;
    private MethodExecutionLoggingHelper helper;

    @BeforeEach
    void setUp() {
        LogMaskingUtil.configure(true, "********", List.of("password"), List.of());
        properties = new LoggingProperties();
        helper = new MethodExecutionLoggingHelper(properties, new ObjectMapper());
    }

    public record Dto(String name, String password) {
    }

    static class Unserializable {
        public Object getSelf() {
            return this;
        }
    }

    @Test
    void exposesAspectFlagsFromProperties() {
        LoggingProperties.AspectProperties aspect = properties.getAspect();
        aspect.setLogEntry(false);
        aspect.setLogExit(false);
        aspect.setLogArgs(false);
        aspect.setLogReturn(false);
        aspect.setLogExceptions(false);
        aspect.setMaxPayloadLength(42);

        assertThat(helper.isLogEntry()).isFalse();
        assertThat(helper.isLogExit()).isFalse();
        assertThat(helper.isLogArgs()).isFalse();
        assertThat(helper.isLogReturn()).isFalse();
        assertThat(helper.isLogExceptions()).isFalse();
        assertThat(helper.getMaxPayloadLength()).isEqualTo(42);
    }

    @Test
    void separatorFallsBackToDefaultWhenBlank() {
        properties.getAspect().setSeparator(" ");
        assertThat(helper.getSeparator()).startsWith("=====").hasSize(80);
        properties.getAspect().setSeparator(null);
        assertThat(helper.getSeparator()).hasSize(80);
        properties.getAspect().setSeparator("--\\r\\n--\\r");
        assertThat(helper.getSeparator()).isEqualTo("--\r\n--\r");
    }

    @Test
    void exclusionByPackagePatternAndTypeKind() {
        assertThat(helper.isExcluded(null)).isFalse();
        assertThat(helper.isExcluded(String.class)).isTrue();
        assertThat(helper.isExcluded(Serializable.class)).isTrue();
        assertThat(helper.isExcluded(MethodExecutionLoggingHelperTest.class)).isFalse();

        properties.getAspect().setExcludedPackages(null);
        properties.getAspect().setExcludedClassPatterns(List.of("", "LoggingHelperTest"));
        assertThat(helper.isExcluded(MethodExecutionLoggingHelperTest.class)).isTrue();

        properties.getAspect().setExcludedClassPatterns(null);
        assertThat(helper.isExcluded(Object.class)).isTrue();
        assertThat(helper.isExcluded(int.class)).isTrue();
    }

    @Test
    void serializesScalarsCollectionsMapsArraysAndObjects() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1);
        map.put("b", null);

        assertThat(helper.serialize(null, 100)).isEqualTo("null");
        assertThat(helper.serialize("text", 100)).isEqualTo("text");
        assertThat(helper.serialize(7, 100)).isEqualTo("7");
        assertThat(helper.serialize(true, 100)).isEqualTo("true");
        assertThat(helper.serialize(new int[] {1, 2}, 100)).isEqualTo("[1, 2]");
        assertThat(helper.serialize(List.of("x", 3), 100)).isEqualTo("[x, 3]");
        assertThat(helper.serialize(map, 100)).isEqualTo("{a=1, b=null}");
        assertThat(helper.serialize(new Dto("n", "secret"), 100))
                .contains("\"name\": \"n\"").contains("\"password\": \"********\"");
        assertThat(helper.serialize("", 100)).isEmpty();
    }

    @Test
    void truncatesLongValuesAndFallsBackForUnserializableObjects() {
        assertThat(helper.serialize("abcdefghij", 4)).isEqualTo("abcd...[truncated]");
        assertThat(helper.serialize(new Unserializable(), 100)).startsWith("Unserializable@");
    }

    @Test
    void formatsJoinPointArguments() {
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.getArgs()).thenReturn(new Object[] {"a", 1});
        assertThat(helper.formatArgs(jp, 100)).isEqualTo("[a, 1]");

        when(jp.getArgs()).thenReturn(new Object[0]);
        assertThat(helper.formatArgs(jp, 100)).isEqualTo("[]");

        when(jp.getArgs()).thenReturn(null);
        assertThat(helper.formatArgs(jp, 100)).isEqualTo("[]");
    }
}
