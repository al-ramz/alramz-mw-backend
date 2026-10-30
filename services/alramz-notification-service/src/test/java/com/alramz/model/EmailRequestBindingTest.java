package com.alramz.model;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailRequestBindingTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void bindsCanonicalFieldNames() {
        final EmailRequest request = mapper.readValue("{\"to\":\"a@x.com,b@y.com\",\"cc\":\"c@z.com\",\"attachments\":"
                + "[{\"fileName\":\"r.pdf\",\"contentType\":\"application/pdf\",\"content\":\"aGVsbG8=\"}]}", EmailRequest.class);

        assertThat(request.getTo()).isEqualTo("a@x.com,b@y.com");
        assertThat(request.getCc()).isEqualTo("c@z.com");
        final EmailAttachment attachment = request.getAttachments().get(0);
        assertThat(attachment.getFileName()).isEqualTo("r.pdf");
        assertThat(attachment.getContentType()).isEqualTo("application/pdf");
        assertThat(new String(attachment.getContent(), StandardCharsets.US_ASCII)).isEqualTo("hello");
    }

    @Test
    void bindsLegacyLowercaseAliases() {
        final EmailAttachment attachment = mapper.readValue(
                "{\"filename\":\"r.pdf\",\"contenttype\":\"application/pdf\",\"content\":\"aGVsbG8=\"}", EmailAttachment.class);

        assertThat(attachment.getFileName()).isEqualTo("r.pdf");
        assertThat(attachment.getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void bindsLegacyNameAlias() {
        final EmailAttachment attachment = mapper.readValue("{\"name\":\"old.pdf\",\"content\":\"aGVsbG8=\"}", EmailAttachment.class);

        assertThat(attachment.getFileName()).isEqualTo("old.pdf");
    }

    @Test
    void invalidBase64PointsAtAttachmentContent() {
        assertThatThrownBy(() -> mapper.readValue(
                "{\"attachments\":[{\"fileName\":\"a\",\"content\":\"%%%\"}]}", EmailRequest.class))
                .isInstanceOfSatisfying(JacksonException.class, ex -> {
                    assertThat(ex.getPath()).hasSize(3);
                    assertThat(ex.getPath().get(0).getPropertyName()).isEqualTo("attachments");
                    assertThat(ex.getPath().get(1).getIndex()).isZero();
                    assertThat(ex.getPath().get(2).getPropertyName()).isEqualTo("content");
                });
    }
}
