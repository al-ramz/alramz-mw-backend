package com.alramz.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ContentDispositionFormatterTest {

    private final ContentDispositionFormatter formatter = new ContentDispositionFormatter();

    @Test
    void asciiName() {
        assertThat(formatter.attachment("PASSPORT_FIT-100234.pdf"))
                .isEqualTo("attachment; filename=\"PASSPORT_FIT-100234.pdf\"; filename*=UTF-8''PASSPORT_FIT-100234.pdf");
    }

    @Test
    void spacesAndReservedCharacters_arePercentEncodedInFilenameStar() {
        assertThat(formatter.attachment("my file (1);v2.pdf"))
                .isEqualTo("attachment; filename=\"my file (1);v2.pdf\"; filename*=UTF-8''my%20file%20%281%29%3Bv2.pdf");
    }

    @Test
    void arabicName_hasAsciiFallbackAndUtf8Encoding() {
        String name = "جواز سفر.pdf";

        String header = formatter.attachment(name);

        assertThat(header).startsWith("attachment; filename=\"____ ___.pdf\"; filename*=UTF-8''%D8%AC%D9%88");
        assertThat(ContentDisposition.parse(header).getFilename()).isEqualTo(name);
        assertThat(ContentDisposition.parse(header).getCharset()).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void quotesBackslashesAndControlCharacters_areReplacedInTheFallback() {
        String header = formatter.attachment("a\"b\\c\td.pdf");

        assertThat(header).startsWith("attachment; filename=\"a_b_c_d.pdf\";");
        assertThat(header).contains("filename*=UTF-8''a%22b%5Cc%09d.pdf");
    }
}
