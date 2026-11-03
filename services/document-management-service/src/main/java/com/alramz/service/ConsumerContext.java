package com.alramz.service;

/** The calling consumer (JWT subject) and its own Open Bee API key. The key never appears in toString(). */
public record ConsumerContext(String consumerId, String apiKey) {

    @Override
    public String toString() {
        return "ConsumerContext[consumerId=" + consumerId + ", apiKey=***]";
    }
}
