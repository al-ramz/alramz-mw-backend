package com.alramz.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.lang.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Generated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * EmailRequest
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-09-17T15:45:48.865288+05:30[Asia/Kolkata]", comments = "Generator version: 7.23.0")
@SuppressWarnings({"PMD.OnlyOneReturn", "PMD.AvoidFieldNameMatchingMethodName"})
public class EmailRequest {

  private String to;

  private String subject;

  private String from;

  private String body;

  private List<@Valid EmailAttachment> attachments = new ArrayList<>();

  public EmailRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public EmailRequest(final String to, final String subject, final String from, final String body) {
    this.to = to;
    this.subject = subject;
    this.from = from;
    this.body = body;
  }

  public EmailRequest to(final String to) {
    this.to = to;
    return this;
  }

  /**
   * Recipient email address
   * @return to
   */
  @NotNull
  @Email
  @Schema(name = "to", example = "recipient@example.com", description = "Recipient email address", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("to")
  public String getTo() {
    return to;
  }

  @JsonProperty("to")
  public void setTo(final String to) {
    this.to = to;
  }

  public EmailRequest subject(final String subject) {
    this.subject = subject;
    return this;
  }

  /**
   * Email subject
   * @return subject
   */
  @NotNull
  @Size(max = 255)
  @Schema(name = "subject", example = "Test Email from Spring Boot", description = "Email subject", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("subject")
  public String getSubject() {
    return subject;
  }

  @JsonProperty("subject")
  public void setSubject(final String subject) {
    this.subject = subject;
  }

  public EmailRequest from(final String from) {
    this.from = from;
    return this;
  }

  /**
   * Sender email address
   * @return from
   */
  @NotNull
  @Email
  @Schema(name = "from", example = "sender@example.com", description = "Sender email address", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("from")
  public String getFrom() {
    return from;
  }

  @JsonProperty("from")
  public void setFrom(final String from) {
    this.from = from;
  }

  public EmailRequest body(final String body) {
    this.body = body;
    return this;
  }

  /**
   * Email body (HTML supported)
   * @return body
   */
  @NotNull
  @Size(max = 10000)
  @Schema(name = "body", example = "<h1>Hello</h1><p>This is a test email body.</p>", description = "Email body (HTML supported)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("body")
  public String getBody() {
    return body;
  }

  @JsonProperty("body")
  public void setBody(final String body) {
    this.body = body;
  }

  public EmailRequest attachments(final List<@Valid EmailAttachment> attachments) {
    this.attachments = attachments != null ? new ArrayList<>(attachments) : new ArrayList<>();
    return this;
  }

  public EmailRequest addAttachmentsItem(final EmailAttachment attachmentsItem) {
    if (this.attachments == null) {
      this.attachments = new ArrayList<>();
    }
    this.attachments.add(attachmentsItem);
    return this;
  }

  /**
   * Optional email attachments
   * @return attachments
   */
  @Valid
  @Size(max = 10)
  @Schema(name = "attachments", description = "Optional email attachments", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("attachments")
  public List<@Valid EmailAttachment> getAttachments() {
    return attachments != null ? new ArrayList<>(attachments) : new ArrayList<>();
  }

  @JsonProperty("attachments")
  public void setAttachments(final List<@Valid EmailAttachment> attachments) {
    this.attachments = attachments != null ? new ArrayList<>(attachments) : new ArrayList<>();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EmailRequest emailRequest = (EmailRequest) o;
    return Objects.equals(this.to, emailRequest.to) &&
        Objects.equals(this.subject, emailRequest.subject) &&
        Objects.equals(this.from, emailRequest.from) &&
        Objects.equals(this.body, emailRequest.body) &&
        Objects.equals(this.attachments, emailRequest.attachments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(to, subject, from, body, attachments);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EmailRequest {\n");
    sb.append("    to: ").append(toIndentedString(to)).append("\n");
    sb.append("    subject: ").append(toIndentedString(subject)).append("\n");
    sb.append("    from: ").append(toIndentedString(from)).append("\n");
    sb.append("    body: ").append(toIndentedString(body)).append("\n");
    sb.append("    attachments: ").append(toIndentedString(attachments)).append("\n");
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
