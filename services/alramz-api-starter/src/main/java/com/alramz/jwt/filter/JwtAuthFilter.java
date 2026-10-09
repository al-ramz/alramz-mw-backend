package com.alramz.jwt.filter;

import com.alramz.jwt.config.JwtProperties;
import com.alramz.jwt.service.TokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import io.jsonwebtoken.JwtException;

public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtProperties properties;
    private final TokenProvider tokenProvider;
    private final SecretKey key;

    public JwtAuthFilter(JwtProperties properties, TokenProvider tokenProvider) {
        this.properties = properties;
        this.tokenProvider = tokenProvider;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        Optional<String> usernameOpt = tokenProvider.validateToken(token);
        if (usernameOpt.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Invalid JWT token for request: {}", request.getRequestURI());
            }
            filterChain.doFilter(request, response);
            return;
        }

        String username = usernameOpt.get();
        List<SimpleGrantedAuthority> authorities = extractAuthorities(token);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(username, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        if (logger.isDebugEnabled()) {
            logger.debug("Authenticated user '{}' for request: {}", username, request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> extractAuthorities(String token) {
        try {
            Claims claims = Jwts.parser() // NOPMD LawOfDemeter
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            List<String> roles = claims.get("roles", List.class); // NOPMD LawOfDemeter
            if (roles == null) {
                return List.of();
            }
            return roles.stream() // NOPMD LawOfDemeter
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        } catch (JwtException e) {
            return List.of();
        }
    }
}
