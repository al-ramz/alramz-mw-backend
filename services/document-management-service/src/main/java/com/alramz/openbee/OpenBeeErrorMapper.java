package com.alramz.openbee;

import com.alramz.exception.DmsException;
import com.alramz.exception.ErrorCode;
import com.alramz.logging.util.MDCUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * The one place implementing brief 7.3: Open Bee failure to DMS code. Open Bee's status and codes are logged
 * with the correlation ID; Open Bee's message is never echoed to the caller.
 */
@Slf4j
public final class OpenBeeErrorMapper {

    static final String BAD_CREDENTIALS = "BAD_CREDENTIALS";
    static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    static final String NOT_HAVE_PERMISSION = "NOT_HAVE_PERMISSION";
    static final String FILING_CATEGORY_IS_NOT_FOUND = "FILING_CATEGORY_IS_NOT_FOUND";
    static final String METADATA_NOT_EXIST = "METADATA_NOT_EXIST";
    static final String MISSING_CHECKSUM = "MISSING_CHECKSUM";
    static final String INVALID_CHECKSUM_ALGO = "INVALID_CHECKSUM_ALGO";
    /** Live Open Bee (V5): 400 with this code when the document exists and overwrite is false. */
    static final String DOCUMENT_EXISTS = "Document with the same name already exist";
    /** Live Open Bee (V2): 400 with this code on a save into a category without an active folder rule. */
    static final String MUST_SPECIFY_FOLDER_OR_RULE = "MUST_SPECIFY_FOLDER_OR_RULE";
    static final String NO_FOLDER_RULE_MESSAGE =
            "Filing category has no active folder rule in the document management system; documents cannot be saved into it";

    private OpenBeeErrorMapper() {
    }

    public static DmsException toDms(OpenBeeException e, OpenBeeOperation op, boolean overwrite) {
        return toDms(e, op, overwrite, null);
    }

    public static DmsException toDms(OpenBeeException e, OpenBeeOperation op, boolean overwrite, String consumerId) {
        ErrorCode code = codeFor(e, op, overwrite);
        String correlationId = MDCUtil.getCorrelationId();
        if (code == ErrorCode.DMS202) {
            log.error("Open Bee rejected the API key of consumer {} on {} (status={}, codes={}, correlationId={})",
                    consumerId, op, e.getHttpStatus(), e.getObCodes(), correlationId);
        } else if (e.hasCode(MISSING_CHECKSUM) || e.hasCode(INVALID_CHECKSUM_ALGO)) {
            log.error("Open Bee rejected the upload checksum on {} (our bug; status={}, codes={}, correlationId={})",
                    op, e.getHttpStatus(), e.getObCodes(), correlationId);
        } else if (code == ErrorCode.DMS201) {
            log.error("Unexpected Open Bee response on {} (kind={}, status={}, codes={}, correlationId={})",
                    op, e.getKind(), e.getHttpStatus(), e.getObCodes(), correlationId);
        } else {
            log.warn("Open Bee failure on {} mapped to {} (kind={}, status={}, codes={}, correlationId={})",
                    op, code.code(), e.getKind(), e.getHttpStatus(), e.getObCodes(), correlationId);
        }
        String message = code == ErrorCode.DMS102 && e.hasCode(MUST_SPECIFY_FOLDER_OR_RULE)
                ? NO_FOLDER_RULE_MESSAGE : code.defaultMessage();
        return new DmsException(code, message, e);
    }

    static ErrorCode codeFor(OpenBeeException e, OpenBeeOperation op, boolean overwrite) {
        switch (e.getKind()) {
            case TIMEOUT, CONNECTION, CIRCUIT_OPEN:
                return ErrorCode.DMS203;
            case BAD_RESPONSE:
                return ErrorCode.DMS201;
            default:
                break;
        }
        int status = e.getHttpStatus();
        if (status == 401 || e.hasCode(BAD_CREDENTIALS)) {
            return ErrorCode.DMS202;
        }
        if (status == 403 || e.hasCode(PERMISSION_DENIED) || e.hasCode(NOT_HAVE_PERMISSION)) {
            return ErrorCode.DMS109;
        }
        if (status >= 500) {
            return ErrorCode.DMS203;
        }
        if (status == 404 && (op == OpenBeeOperation.DOWNLOAD || op == OpenBeeOperation.DOCUMENT_INFO)) {
            return ErrorCode.DMS108;
        }
        if (e.hasCode(FILING_CATEGORY_IS_NOT_FOUND)
                || (op == OpenBeeOperation.SAVE && e.hasCode(MUST_SPECIFY_FOLDER_OR_RULE))) {
            return ErrorCode.DMS102;
        }
        if (e.hasCode(METADATA_NOT_EXIST)) {
            return ErrorCode.DMS103;
        }
        if ((status == 409 || e.hasCode(DOCUMENT_EXISTS)) && op == OpenBeeOperation.SAVE && !overwrite) {
            return ErrorCode.DMS106;
        }
        return ErrorCode.DMS201;
    }
}
