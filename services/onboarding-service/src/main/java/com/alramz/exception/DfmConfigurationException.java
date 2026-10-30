package com.alramz.exception;

/** A required adapter.dfm-member-api setting is blank or insecure (DFN012). The message names the key, never its value. */
public class DfmConfigurationException extends RuntimeException {

    public DfmConfigurationException(String message) {
        super(message);
    }
}
