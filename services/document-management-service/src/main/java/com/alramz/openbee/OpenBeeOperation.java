package com.alramz.openbee;

/** The Open Bee call that failed; some status codes mean different things per operation (brief 7.3). */
public enum OpenBeeOperation {
    LIST_CATEGORIES,
    GET_LIST,
    SAVE,
    SEARCH,
    DOWNLOAD,
    DOCUMENT_INFO
}
