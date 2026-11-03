package com.alramz.scheduler.config;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import com.alramz.scheduler.EnableScheduler;

public class SchedulerConfigCondition extends SpringBootCondition {
    private static final Logger logger = LoggerFactory.getLogger(SchedulerConfigCondition.class);

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext conditionContext, AnnotatedTypeMetadata annotatedTypeMetadata) {
        ConditionMessage.Builder schedulerConfigCondition = ConditionMessage.forCondition("schedulerEnabled");
        Map<String, Object> schedulerEnabledBeans = conditionContext.getBeanFactory().getBeansWithAnnotation(EnableScheduler.class);
        if (MapUtils.isNotEmpty(schedulerEnabledBeans) && hasJobGroupArguments(schedulerEnabledBeans.values())) {
            logger.info("Found @EnableScheduler, so creating scheduler service instance");
            return ConditionOutcome.match(schedulerConfigCondition
                    .available("Found @EnableScheduler, so creating scheduler service instance"));
        }
        logger.info("Scheduler details are not configured. So skipping scheduler bean creations");
        return ConditionOutcome.noMatch(schedulerConfigCondition.notAvailable("Disabling scheduler instance creation"));
    }

    private boolean hasJobGroupArguments(Collection<Object> capturedSchedulers) {
        Optional<String> jobGroupNameMetaData = capturedSchedulers.stream()
                .map(Object::getClass)
                .map(Class::getDeclaredAnnotations)
                .flatMap(Stream::of)
                .filter(EnableScheduler.class::isInstance)
                .map(EnableScheduler.class::cast)
                .map(EnableScheduler::jobGroupName).findAny();

        String jobGroupName = Optional.ofNullable(System.getProperty("JobGroupName"))
                .orElse(jobGroupNameMetaData.orElse(null));

        if (StringUtils.isEmpty(jobGroupName)) {
            logger.error("If @EnableScheduler specified at least one JobGroupName name should be specified via vm argument or annotation parameter");
        } else {
            System.setProperty("JobGroupName", jobGroupName);
        }
        return StringUtils.isNoneEmpty(jobGroupName);
    }
}