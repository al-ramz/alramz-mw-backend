package com.alramz.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.util.unit.DataSize;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** The spec 6.1 table: 14 codes, each with its HTTP status and default message. */
class ErrorCodeTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "DMS101|400|Invalid request",
            "DMS102|400|Filing category is not available to this consumer",
            "DMS103|400|Metadata is not defined on this filing category",
            "DMS104|400|Mandatory metadata is missing",
            "DMS105|400|Invalid metadata value",
            "DMS106|409|A document with this name already exists and overwrite is false",
            "DMS107|413|File exceeds the maximum size of %s",
            "DMS108|404|Document not found",
            "DMS109|403|Consumer is not allowed to perform this operation",
            "DMS110|400|Metadata type not supported by this service",
            "DMS201|502|Unexpected response from the document management system",
            "DMS202|502|Document management system rejected the service credentials",
            "DMS203|503|Document management system unavailable",
            "DMS299|500|Internal Server Error"
    })
    void table(ErrorCode code, int status, String message) {
        assertThat(code.code()).isEqualTo(code.name());
        assertThat(code.status().value()).isEqualTo(status);
        assertThat(code.defaultMessage()).isEqualTo(message);
    }

    @Test
    void exactlyFourteenUniqueCodes() {
        assertThat(ErrorCode.values()).hasSize(14);
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::code).distinct()).hasSize(14)
                .allMatch(c -> c.matches("DMS\\d{3}"));
    }

    @Test
    void fileTooLargeMessage_isRenderedFromTheConfiguredLimit() {
        assertThat(ErrorCode.fileTooLargeMessage(DataSize.ofMegabytes(10))).isEqualTo("File exceeds the maximum size of 10 MB");
        assertThat(ErrorCode.fileTooLargeMessage(DataSize.ofKilobytes(512))).isEqualTo("File exceeds the maximum size of 512 KB");
        assertThat(ErrorCode.fileTooLargeMessage(DataSize.ofBytes(100))).isEqualTo("File exceeds the maximum size of 100 bytes");
        assertThat(ErrorCode.fileTooLargeMessage(null)).isEqualTo("File exceeds the maximum size of 0 bytes");
    }

    @Test
    void dmsException_defaultsToTheCodeMessage() {
        DmsException e = new DmsException(ErrorCode.DMS108);

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DMS108);
        assertThat(e.getMessage()).isEqualTo("Document not found");
        assertThat(new DmsException(ErrorCode.DMS101, "x", new IllegalStateException()).getCause()).isNotNull();
    }
}
