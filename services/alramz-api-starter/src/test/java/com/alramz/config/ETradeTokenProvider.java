package com.alramz.config;

/**
 * Stand-in for the onboarding-service class of the same name, which ApiAuditAspect's pointcut names.
 * AspectJ rejects the whole expression when any named type is missing, so these let the starter tests
 * evaluate the pointcut the way it resolves inside onboarding-service.
 */
public class ETradeTokenProvider {

    public String fetchAndCacheToken() {
        return "token";
    }
}
