package com.alramz.jwt.config;

import com.alramz.jwt.aspect.JwtSecuredAspect;
import com.alramz.jwt.context.JwtContext;
import com.alramz.jwt.filter.JwtAuthFilter;
import com.alramz.jwt.service.AuthService;
import com.alramz.jwt.service.PasswordEncoderService;
import com.alramz.jwt.service.TokenProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
@EntityScan("com.alramz.jwt.model")
@EnableJpaRepositories(basePackages = "com.alramz.jwt.repository")
@ComponentScan(
        basePackages = "com.alramz.jwt",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
)
@EnableMethodSecurity
public class JwtAutoConfiguration {

    @Bean
    TokenProvider tokenProvider(JwtProperties properties) {
        return new TokenProvider(properties);
    }

    @Bean
    PasswordEncoderService passwordEncoderService() {
        return new PasswordEncoderService();
    }

    @Bean
    com.alramz.jwt.repository.UserRepository userRepository(com.alramz.jwt.repository.UserJpaRepository jpaRepository) {
        return new com.alramz.jwt.repository.UserRepository(jpaRepository);
    }

    @Bean
    com.alramz.jwt.repository.RefreshTokenRepository refreshTokenRepository(com.alramz.jwt.repository.RefreshTokenJpaRepository jpaRepository) {
        return new com.alramz.jwt.repository.RefreshTokenRepository(jpaRepository);
    }

    @Bean
    AuthService authService(com.alramz.jwt.repository.UserRepositoryOps userRepository,
                            PasswordEncoderService passwordEncoder,
                            TokenProvider tokenProvider,
                            com.alramz.jwt.repository.RefreshTokenRepositoryOps refreshTokenRepository) {
        return new AuthService(userRepository, passwordEncoder, tokenProvider, refreshTokenRepository);
    }

    @Bean
    JwtContext jwtContext(JwtProperties properties, TokenProvider tokenProvider) {
        return new JwtContext(properties, tokenProvider);
    }

    @Bean
    JwtSecuredAspect jwtSecuredAspect(JwtContext jwtContext) {
        return new JwtSecuredAspect(jwtContext);
    }

    @Bean
    JwtAuthFilter jwtAuthFilter(JwtProperties properties, TokenProvider tokenProvider) {
        return new JwtAuthFilter(properties, tokenProvider);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(SecurityFilterChain.class)
    SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter, JwtProperties properties) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(properties.getLoginUrl()).permitAll()
                        .requestMatchers(properties.getRegisterUrl()).permitAll()
                        .requestMatchers(properties.getRefreshUrl()).permitAll();
                    for (String url : properties.getPermitAllUrls()) {
                        auth.requestMatchers(url).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
