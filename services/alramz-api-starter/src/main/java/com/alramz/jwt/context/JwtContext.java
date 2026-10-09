package com.alramz.jwt.context;

import com.alramz.jwt.config.JwtProperties;
import com.alramz.jwt.service.TokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import io.jsonwebtoken.JwtException;

public class JwtContext {

    private static final Logger logger = LoggerFactory.getLogger(JwtContext.class);

    private final JwtProperties properties;
    private final TokenProvider tokenProvider;

    public JwtContext(JwtProperties properties, TokenProvider tokenProvider) {
        this.properties = properties;
        this.tokenProvider = tokenProvider;
    }

    public String getCurrentApplication() {
        return getClaim(properties.getApplicationClaimName());
    }

    public String getCurrentEnvironment() {
        return getClaim(properties.getEnvironmentClaimName());
    }

    public String getClaim(String claimName) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("No request attributes found for claim '{}'", claimName);
                }
                return null;
            }
            HttpServletRequest request = attributes.getRequest(); // NOPMD LawOfDemeter
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                if (logger.isDebugEnabled()) {
                    logger.debug("No Bearer token found for claim '{}'", claimName);
                }
                return null;
            }
            String token = header.substring(7);
            String claimValue = tokenProvider.extractClaim(token, claimName);
            if (logger.isDebugEnabled()) {
                logger.debug("Extracted claim '{}' = '{}'", claimName, claimValue);
            }
            return claimValue;
        } catch (JwtException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to extract claim '{}': {}", claimName, e.getMessage());
            }
            return null;
        }
    }
}
