package com.alramz.service;

import com.alramz.client.UserRequestContext;
import com.alramz.config.DmsProperties;
import com.alramz.exception.DmsException;
import com.alramz.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the calling consumer from the starter JWT subject (decision Q1) and looks up its Open Bee key in
 * {@code dms.consumers}, by exact, case-sensitive match. An unconfigured subject gets DMS109 before any Open Bee
 * call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumerContextResolver {

    private final DmsProperties properties;

    public ConsumerContext resolve() {
        String subject = UserRequestContext.get().getUserId();
        ConsumerContext consumer = findByConsumerId(subject).orElseThrow(() -> {
            log.warn("JWT subject {} is not a configured DMS consumer", subject);
            return new DmsException(ErrorCode.DMS109);
        });
        DmsRequestSummary.current().ifPresent(summary -> summary.setConsumerId(consumer.consumerId()));
        return consumer;
    }

    public Optional<ConsumerContext> findByConsumerId(String consumerId) {
        if (consumerId == null) {
            return Optional.empty();
        }
        DmsProperties.Consumer consumer = properties.getConsumers().get(consumerId);
        if (consumer == null || consumer.getApiKey() == null || consumer.getApiKey().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ConsumerContext(consumerId, consumer.getApiKey()));
    }
}
