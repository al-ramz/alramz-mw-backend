package com.alramz.service;

import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Open Bee metadata type names grouped by how this service validates and encodes them (brief 8.1). */
@Slf4j
public enum MetadataTypeGroup {
    TEXT, INTEGER, DECIMAL, DATE, BOOLEAN, LIST, SUBLIST, UNSUPPORTED;

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    public static MetadataTypeGroup fromOpenBeeType(String type) {
        String name = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        switch (name) {
            case "VARCHAR", "TEXT", "EMAIL", "ADDRESS":
                return TEXT;
            case "INT", "INTEGER", "AUTOINCREMENT":
                return INTEGER;
            case "DECIMAL":
                return DECIMAL;
            case "DATE":
                return DATE;
            case "BOOLEAN":
                return BOOLEAN;
            case "LIST", "MULTIPLE_CHOICE":
                return LIST;
            case "SUBLIST":
                return SUBLIST;
            case "USER_GROUP", "FILE":
                return UNSUPPORTED;
            default:
                if (WARNED.add(name)) {
                    log.warn("Unknown Open Bee metadata type '{}' treated as text", name);
                }
                return TEXT;
        }
    }

    /** Groups whose search filter supports several values (IN). */
    public boolean supportsMultiValueSearch() {
        return this == TEXT || this == LIST || this == SUBLIST;
    }
}
