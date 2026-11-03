package com.alramz.jwt.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = JwtProperties.PREFIX)
public class JwtProperties {

    public static final String PREFIX = "company.jwt";

    private boolean enabled = true;

    private String secret = "defaultSecretKeyChangeMeInProduction1234567890";

    private long accessTokenExpirationMs = 15L * 60 * 1000;

    private long refreshTokenExpirationMs = 7L * 24 * 60 * 60 * 1000;

    private String loginUrl = "/api/auth/login";

    private String registerUrl = "/api/auth/register";

    /** Self-registration accepts caller-supplied roles; keep off outside controlled environments. */
    private boolean registrationEnabled = false;

    private String refreshUrl = "/api/auth/refresh";

    private String logoutUrl = "/api/auth/logout";

    private String validateUrl = "/api/auth/validate";

    private List<String> permitAllUrls = new ArrayList<>();

    private String rolePrefix = "ROLE_";

    private String environmentClaimName = "environment";

    private String applicationClaimName = "application";
}
