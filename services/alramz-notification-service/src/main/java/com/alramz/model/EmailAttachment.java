package com.alramz.model;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.lang.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Generated;
import jakarta.validation.constraints.NotNull;

/**
 * EmailAttachment
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-09-17T15:45:48.865288+05:30[Asia/Kolkata]", comments = "Generator version: 7.23.0")
@SuppressWarnings({"PMD.AvoidFieldNameMatchingMethodName", "PMD.OnlyOneReturn"})
public class EmailAttachment {

  private String name;

  private byte[] content;

  public EmailAttachment() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public EmailAttachment(final String name, final byte[] content) {
    this.name = name;
    this.content = content != null ? content.clone() : new byte[0];
  }

  public EmailAttachment name(final String name) {
    this.name = name;
    return this;
  }

  /**
   * Attachment file name
   * @return name
   */
  @NotNull
  @Schema(name = "name", example = "document.pdf", description = "Attachment file name", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("name")
  public String getName() {
    return name;
  }

  @JsonProperty("name")
  public void setName(final String name) {
    this.name = name;
  }

  public EmailAttachment content(final byte[] content) {
    this.content = content != null ? content.clone() : new byte[0];
    return this;
  }

  /**
   * Base64-encoded attachment content
   * @return content
   */
  @NotNull
  @Schema(name = "content", example = "[B@50f2287d", description = "Base64-encoded attachment content", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("content")
  public byte[] getContent() {
    return content != null ? content.clone() : new byte[0];
  }

  @JsonProperty("content")
  public void setContent(final byte[] content) {
    this.content = content != null ? content.clone() : new byte[0];
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EmailAttachment emailAttachment = (EmailAttachment) o;
    return Objects.equals(this.name, emailAttachment.name) &&
        Arrays.equals(this.content, emailAttachment.content);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, Arrays.hashCode(content));
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EmailAttachment {\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    content: ").append(toIndentedString(content)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(@Nullable Object o) {
    return o == null ? "null" : o.toString().replace("\n", "\n    ");
  }
}
