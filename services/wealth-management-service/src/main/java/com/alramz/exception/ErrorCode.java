package com.alramz.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * Every MMF### errorCode this service returns in {@code GenericResponse}.
 *
 * <p>Framework-raised input failures are resolved by (operationId, input, kind), not by field name
 * alone: {@code accountNumber} and {@code source} appear in more than one operation with different
 * codes. The operationId is the controller method name, which the generator copies from the YAML.
 */
public enum ErrorCode {

    ACCOUNT_NUMBER_INVALID("MMF001", "accountNumber must be 1-9 digits"),
    BOOK_MISSING("MMF002", "book is required"),
    BOOK_INVALID("MMF003", "book must be CAPITAL or MMF"),
    ACCOUNT_NOT_FOUND("MMF004", "Account not found"),
    ACCOUNT_INTERNAL_ERROR("MMF005", ErrorCode.INTERNAL_MSG),
    ACCOUNT_UNAVAILABLE("MMF006", ErrorCode.UNAVAILABLE_MSG),

    SWEEP_IN_DATES_INCOMPLETE("MMF010", ErrorCode.DATES_INCOMPLETE_MSG),
    SWEEP_IN_DATE_INVALID("MMF011", ErrorCode.DATE_INVALID_MSG),
    SWEEP_IN_RANGE_INVALID("MMF012", ErrorCode.RANGE_INVALID_MSG),
    SWEEP_IN_INTERNAL_ERROR("MMF015", ErrorCode.INTERNAL_MSG),
    SWEEP_IN_UNAVAILABLE("MMF016", ErrorCode.UNAVAILABLE_MSG),

    SWEEP_OUT_DATES_INCOMPLETE("MMF020", ErrorCode.DATES_INCOMPLETE_MSG),
    SWEEP_OUT_DATE_INVALID("MMF021", ErrorCode.DATE_INVALID_MSG),
    SWEEP_OUT_RANGE_INVALID("MMF022", ErrorCode.RANGE_INVALID_MSG),
    SWEEP_OUT_INTERNAL_ERROR("MMF025", ErrorCode.INTERNAL_MSG),
    SWEEP_OUT_UNAVAILABLE("MMF026", ErrorCode.UNAVAILABLE_MSG),

    CONFIRM_ITEM_COUNT_INVALID("MMF030", "Request must contain 1-1000 items"),
    CONFIRM_ITEM_INVALID("MMF031", "source must be CAPITAL or MMF"),
    CONFIRM_SWEEP_NO_INVALID("MMF032", "sweepNo must be a positive integer"),
    CONFIRM_CONFLICT("MMF033", "Sweeps unknown or not pending"),
    CONFIRM_INTERNAL_ERROR("MMF035", ErrorCode.INTERNAL_MSG),
    CONFIRM_UNAVAILABLE("MMF036", ErrorCode.UNAVAILABLE_MSG),

    PROFIT_ITEM_COUNT_INVALID("MMF040", "Request must contain 1-5000 items"),
    PROFIT_ITEM_INVALID("MMF041", "amount must be > 0 with at most 3 decimals"),
    PROFIT_ACCOUNT_NUMBER_INVALID("MMF042", "accountNumber must be 1-9 digits"),
    PROFIT_SOURCE_INVALID("MMF043", "source must be CAPITAL or MMF"),
    PROFIT_DUPLICATE_REFERENCE("MMF044", "referenceId already booked"),
    PROFIT_INTERNAL_ERROR("MMF045", ErrorCode.INTERNAL_MSG),
    PROFIT_UNAVAILABLE("MMF046", ErrorCode.UNAVAILABLE_MSG),
    PROFIT_ITEM_FAILED("MMF047", "Item could not be created"),

    CASH_CLIENT_ID_MISSING("MMF050", "At least one clientId is required"),
    CASH_CLIENT_ID_INVALID("MMF051", "clientId must be 1-9 digits"),
    CASH_CLIENT_ID_TOO_MANY("MMF052", "At most 500 clientId values are allowed"),
    CASH_INTERNAL_ERROR("MMF055", ErrorCode.INTERNAL_MSG),
    CASH_UNAVAILABLE("MMF056", ErrorCode.UNAVAILABLE_MSG),

    POSITIONS_AS_OF_DATE_INVALID("MMF060", "asOfDate must be an ISO-8601 date not in the future"),
    POSITIONS_CLIENTS_NOT_CONFIGURED("MMF065", "Custodian client list is not configured"),
    POSITIONS_INTERNAL_ERROR("MMF066", ErrorCode.INTERNAL_MSG),
    POSITIONS_UNAVAILABLE("MMF067", ErrorCode.UNAVAILABLE_MSG),

    INVALID_REQUEST("MMF090", "Invalid request");

    /** How a framework input failure presents: absent, wrong count, or anything else (format/pattern/type). */
    public enum Kind { MISSING, SIZE, ANY }

    public static final String OP_ACCOUNT = "getMmfAccount";
    public static final String OP_SWEEP_INS = "listMmfSweepIns";
    public static final String OP_SWEEP_OUTS = "listMmfSweepOuts";
    public static final String OP_CONFIRM = "confirmMmfSweepIns";
    public static final String OP_PROFIT = "createMmfProfitDistributions";
    public static final String OP_CASH = "listMmfCustodianCash";
    public static final String OP_POSITIONS = "listMmfCustodianPositions";

    /** Input key for a whole request body (empty, missing, unreadable, or wrong item count). */
    public static final String BODY = "body";

    private static final String INTERNAL = "#internal";
    private static final String UNAVAILABLE = "#unavailable";

    // Compile-time constants, so the enum constants above may reference them by qualified name.
    private static final String INTERNAL_MSG = "Internal Server Error";
    private static final String UNAVAILABLE_MSG = "MMF data source unavailable";
    private static final String DATES_INCOMPLETE_MSG = "Provide both fromDate and toDate, or neither";
    private static final String DATE_INVALID_MSG = "fromDate must be an ISO-8601 date (yyyy-MM-dd)";
    private static final String RANGE_INVALID_MSG = "Date range must not exceed 93 days and fromDate must not be after toDate";

    private record Rule(ErrorCode code, String message) { }

    private static final Map<String, Rule> RULES = new HashMap<>();

    static {
        rule(OP_ACCOUNT, "accountNumber", Kind.ANY, ACCOUNT_NUMBER_INVALID, null);
        rule(OP_ACCOUNT, "book", Kind.MISSING, BOOK_MISSING, null);
        rule(OP_ACCOUNT, "book", Kind.ANY, BOOK_INVALID, null);
        backend(OP_ACCOUNT, ACCOUNT_INTERNAL_ERROR, ACCOUNT_UNAVAILABLE);

        rule(OP_SWEEP_INS, "fromDate", Kind.ANY, SWEEP_IN_DATE_INVALID, "fromDate must be an ISO-8601 date (yyyy-MM-dd)");
        rule(OP_SWEEP_INS, "toDate", Kind.ANY, SWEEP_IN_DATE_INVALID, "toDate must be an ISO-8601 date (yyyy-MM-dd)");
        backend(OP_SWEEP_INS, SWEEP_IN_INTERNAL_ERROR, SWEEP_IN_UNAVAILABLE);

        rule(OP_SWEEP_OUTS, "fromDate", Kind.ANY, SWEEP_OUT_DATE_INVALID, "fromDate must be an ISO-8601 date (yyyy-MM-dd)");
        rule(OP_SWEEP_OUTS, "toDate", Kind.ANY, SWEEP_OUT_DATE_INVALID, "toDate must be an ISO-8601 date (yyyy-MM-dd)");
        backend(OP_SWEEP_OUTS, SWEEP_OUT_INTERNAL_ERROR, SWEEP_OUT_UNAVAILABLE);

        rule(OP_CONFIRM, BODY, Kind.ANY, CONFIRM_ITEM_COUNT_INVALID, null);
        rule(OP_CONFIRM, "sweepNo", Kind.MISSING, CONFIRM_ITEM_INVALID, "sweepNo is required");
        rule(OP_CONFIRM, "source", Kind.ANY, CONFIRM_ITEM_INVALID, null);
        rule(OP_CONFIRM, "sweepNo", Kind.ANY, CONFIRM_SWEEP_NO_INVALID, null);
        backend(OP_CONFIRM, CONFIRM_INTERNAL_ERROR, CONFIRM_UNAVAILABLE);

        rule(OP_PROFIT, BODY, Kind.ANY, PROFIT_ITEM_COUNT_INVALID, null);
        rule(OP_PROFIT, "sweepDate", Kind.ANY, PROFIT_ITEM_INVALID, "sweepDate must be an ISO-8601 date (yyyy-MM-dd)");
        rule(OP_PROFIT, "amount", Kind.ANY, PROFIT_ITEM_INVALID, null);
        rule(OP_PROFIT, "referenceId", Kind.ANY, PROFIT_ITEM_INVALID, "referenceId must be 1-15 digits");
        rule(OP_PROFIT, "accountNumber", Kind.ANY, PROFIT_ACCOUNT_NUMBER_INVALID, null);
        rule(OP_PROFIT, "source", Kind.ANY, PROFIT_SOURCE_INVALID, null);
        backend(OP_PROFIT, PROFIT_INTERNAL_ERROR, PROFIT_UNAVAILABLE);

        rule(OP_CASH, "clientId", Kind.MISSING, CASH_CLIENT_ID_MISSING, null);
        rule(OP_CASH, "clientId", Kind.SIZE, CASH_CLIENT_ID_TOO_MANY, null);
        rule(OP_CASH, "clientId", Kind.ANY, CASH_CLIENT_ID_INVALID, null);
        backend(OP_CASH, CASH_INTERNAL_ERROR, CASH_UNAVAILABLE);

        rule(OP_POSITIONS, "asOfDate", Kind.ANY, POSITIONS_AS_OF_DATE_INVALID, null);
        backend(OP_POSITIONS, POSITIONS_INTERNAL_ERROR, POSITIONS_UNAVAILABLE);
    }

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    /** Code for a framework-raised input failure: exact (op, input, kind), then (op, input, ANY), else MMF090. */
    public static ErrorCode forInput(String operationId, String input, Kind kind) {
        return lookup(operationId, input, kind).code();
    }

    /** Client-facing message for the same lookup (without any "Item n: " prefix). */
    public static String inputMessage(String operationId, String input, Kind kind) {
        return lookup(operationId, input, kind).message();
    }

    /** The operation's 500 code; MMF090 when the operation is unknown. */
    public static ErrorCode internalFor(String operationId) {
        return lookup(operationId, INTERNAL, Kind.ANY).code();
    }

    /** The operation's 503 code; MMF090 when the operation is unknown. */
    public static ErrorCode unavailableFor(String operationId) {
        return lookup(operationId, UNAVAILABLE, Kind.ANY).code();
    }

    private static Rule lookup(String operationId, String input, Kind kind) {
        Rule rule = RULES.get(key(operationId, input, kind));
        if (rule == null) {
            rule = RULES.get(key(operationId, input, Kind.ANY));
        }
        return rule != null ? rule : new Rule(INVALID_REQUEST, INVALID_REQUEST.defaultMessage);
    }

    private static void rule(String op, String input, Kind kind, ErrorCode code, String message) {
        RULES.put(key(op, input, kind), new Rule(code, message != null ? message : code.defaultMessage));
    }

    private static void backend(String op, ErrorCode internal, ErrorCode unavailable) {
        rule(op, INTERNAL, Kind.ANY, internal, null);
        rule(op, UNAVAILABLE, Kind.ANY, unavailable, null);
    }

    private static String key(String op, String input, Kind kind) {
        return op + '|' + input + '|' + kind;
    }
}
