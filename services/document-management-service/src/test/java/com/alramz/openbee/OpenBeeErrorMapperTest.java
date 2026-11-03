package com.alramz.openbee;

import com.alramz.exception.DmsException;
import com.alramz.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Every row of the brief 7.3 mapping table. */
class OpenBeeErrorMapperTest {

    @ParameterizedTest(name = "{0} {1} on {2} overwrite={3} -> {4}")
    @CsvSource({
            "401, '', LIST_CATEGORIES, true, DMS202",
            "400, BAD_CREDENTIALS, SEARCH, true, DMS202",
            "403, '', SAVE, true, DMS109",
            "400, PERMISSION_DENIED, SEARCH, true, DMS109",
            "400, NOT_HAVE_PERMISSION, DOWNLOAD, true, DMS109",
            "404, '', DOWNLOAD, true, DMS108",
            "404, '', DOCUMENT_INFO, true, DMS108",
            "404, '', GET_LIST, true, DMS201",
            "404, '', SEARCH, true, DMS201",
            "400, FILING_CATEGORY_IS_NOT_FOUND, SAVE, true, DMS102",
            "400, METADATA_NOT_EXIST, SEARCH, true, DMS103",
            "409, '', SAVE, false, DMS106",
            "400, 'Document with the same name already exist', SAVE, false, DMS106",
            "400, 'Document with the same name already exist', SAVE, true, DMS201",
            "400, MUST_SPECIFY_FOLDER_OR_RULE, SEARCH, true, DMS201",
            "409, '', SAVE, true, DMS201",
            "409, '', SEARCH, false, DMS201",
            "400, MISSING_CHECKSUM, SAVE, true, DMS201",
            "400, INVALID_CHECKSUM_ALGO, SAVE, true, DMS201",
            "400, SOMETHING_ELSE, SAVE, true, DMS201",
            "422, '', SAVE, true, DMS201",
            "500, '', SAVE, true, DMS203",
            "502, '', LIST_CATEGORIES, true, DMS203",
            "503, '', DOWNLOAD, true, DMS203"
    })
    void httpErrors(int status, String code, OpenBeeOperation op, boolean overwrite, ErrorCode expected) {
        OpenBeeException e = new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, status,
                code.isEmpty() ? List.of() : List.of(code), "/endpoint");

        DmsException dms = OpenBeeErrorMapper.toDms(e, op, overwrite, "crm");

        assertThat(dms.getErrorCode()).isEqualTo(expected);
        assertThat(dms.getMessage()).isEqualTo(expected.defaultMessage());
        assertThat(dms.getCause()).isSameAs(e);
    }

    /** Live Open Bee (V2): a save into a category without an active folder rule is a client error with a clear message. */
    @Test
    void saveIntoCategoryWithoutFolderRule_isDms102WithAClearMessage() {
        OpenBeeException e = new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 400,
                List.of("MUST_SPECIFY_FOLDER_OR_RULE"), "/browse/ws/document");

        DmsException dms = OpenBeeErrorMapper.toDms(e, OpenBeeOperation.SAVE, true, "crm");

        assertThat(dms.getErrorCode()).isEqualTo(ErrorCode.DMS102);
        assertThat(dms.getMessage()).isEqualTo(OpenBeeErrorMapper.NO_FOLDER_RULE_MESSAGE);
    }

    @ParameterizedTest
    @EnumSource(value = OpenBeeException.Kind.class, names = {"TIMEOUT", "CONNECTION", "CIRCUIT_OPEN"})
    void unavailable_isDms203(OpenBeeException.Kind kind) {
        OpenBeeException e = new OpenBeeException(kind, 0, List.of(), "/endpoint");

        assertThat(OpenBeeErrorMapper.toDms(e, OpenBeeOperation.SAVE, true).getErrorCode()).isEqualTo(ErrorCode.DMS203);
    }

    @Test
    void badResponse_isDms201() {
        OpenBeeException e = new OpenBeeException(OpenBeeException.Kind.BAD_RESPONSE, 201, null, "/browse/ws/document");

        assertThat(OpenBeeErrorMapper.toDms(e, OpenBeeOperation.SAVE, true).getErrorCode()).isEqualTo(ErrorCode.DMS201);
        assertThat(e.getObCodes()).isEmpty();
    }

    @Test
    void openBeeMessage_isNeverEchoed() {
        OpenBeeException e = new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 400,
                List.of("FILING_CATEGORY_IS_NOT_FOUND"), "/x");

        DmsException dms = OpenBeeErrorMapper.toDms(e, OpenBeeOperation.SAVE, true);

        assertThat(dms.getMessage()).doesNotContain("FILING_CATEGORY_IS_NOT_FOUND").doesNotContain("/x");
    }

    @Test
    void transientAndUnavailableClassification() {
        assertThat(new OpenBeeException(OpenBeeException.Kind.CIRCUIT_OPEN, 0, List.of(), "/x").isTransient()).isFalse();
        assertThat(new OpenBeeException(OpenBeeException.Kind.CIRCUIT_OPEN, 0, List.of(), "/x").isUnavailable()).isTrue();
        assertThat(new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 404, List.of(), "/x").isTransient()).isFalse();
        assertThat(new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 500, List.of(), "/x").isTransient()).isTrue();
        assertThat(new OpenBeeException(OpenBeeException.Kind.BAD_RESPONSE, 200, List.of(), "/x").isUnavailable()).isFalse();
    }
}
