package com.alramz.exception;

/** A required adapter.dfm-member-api setting is blank or insecure (DFN012). The message names the key, never its value. */
public class DfmConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DfmConfigurationException(String message) {
        super(message);
    }
}
