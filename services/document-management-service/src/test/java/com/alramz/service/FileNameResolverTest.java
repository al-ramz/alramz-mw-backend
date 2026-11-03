package com.alramz.service;

import com.alramz.exception.DmsException;
import com.alramz.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Brief 6.2 / 11.1 file-name rules (E18, E19). */
class FileNameResolverTest {

    private final FileNameResolver resolver = new FileNameResolver();

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @CsvSource(nullValues = "NULL", value = {
            "passport.pdf, scan.jpg, passport.pdf",
            "passport, scan.PDF, passport.PDF",
            "passport, NULL, passport",
            "passport, noext, passport",
            "'  Passport Copy.PDF  ', x.pdf, Passport Copy.PDF",
            "NULL, scan.pdf, scan.pdf",
            "NULL, C:\\Users\\clerk\\Desktop\\scan.pdf, scan.pdf",
            "NULL, /home/clerk/MiXeD Case.Pdf, MiXeD Case.Pdf",
            "جواز.pdf, x.bin, جواز.pdf"
    })
    void resolves(String requested, String original, String expected) {
        assertThat(resolver.resolve(requested, original)).isEqualTo(expected);
    }

    @Test
    void noNameAtAll_isDms101FileNameRequired() {
        assertError(() -> resolver.resolve(null, null), "fileName is required");
        assertError(() -> resolver.resolve(null, "C:\\dir\\"), "fileName is required");
        assertError(() -> resolver.resolve(null, "   "), "fileName is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../etc/passwd", "a/b.pdf", "a\\b.pdf", "line\nbreak.pdf", "tab\t.pdf", "nul\u0000.pdf", "del\u007f.pdf"})
    void separatorsAndControlCharacters_areDms101(String name) {
        assertError(() -> resolver.resolve(name, "x.pdf"), "fileName must not contain path separators or control characters");
    }

    @Test
    void blank_isDms101() {
        assertError(() -> resolver.resolve("   ", "x.pdf"), "fileName must not be blank");
        assertError(() -> resolver.resolve("", null), "fileName must not be blank");
    }

    @Test
    void tooLong_isDms101() {
        assertThat(resolver.resolve("a".repeat(251) + ".pdf", null)).hasSize(255);
        assertError(() -> resolver.resolve("a".repeat(252) + ".pdf", null), "fileName must be at most 255 characters");
        assertError(() -> resolver.resolve("a".repeat(252), "x.pdf"), "fileName must be at most 255 characters");
    }

    @Test
    void extension() {
        assertThat(FileNameResolver.extension("a.pdf")).isEqualTo("pdf");
        assertThat(FileNameResolver.extension("a.tar.gz")).isEqualTo("gz");
        assertThat(FileNameResolver.extension(".hidden")).isNull();
        assertThat(FileNameResolver.extension("trailing.")).isNull();
        assertThat(FileNameResolver.extension(null)).isNull();
    }

    @Test
    void withExtension() {
        assertThat(FileNameResolver.withExtension("testfile", "txt")).isEqualTo("testfile.txt");
        assertThat(FileNameResolver.withExtension("A.PDF", "pdf")).isEqualTo("A.PDF");
        assertThat(FileNameResolver.withExtension("a", " ")).isEqualTo("a");
        assertThat(FileNameResolver.withExtension("a", null)).isEqualTo("a");
        assertThat(FileNameResolver.withExtension(null, "pdf")).isNull();
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DmsException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DMS101);
            assertThat(e.getMessage()).isEqualTo(message);
        });
    }
}
