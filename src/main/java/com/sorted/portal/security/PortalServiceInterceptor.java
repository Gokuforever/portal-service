package com.sorted.portal.security;

import com.sorted.commons.jwt.JwtTokenUtil;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.sorted.portal.service.CookieService.createSecureCookie;

@Component
public class PortalServiceInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(PortalServiceInterceptor.class);
    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    // Rate limiting for failed attempts
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long RATE_LIMIT_WINDOW_MS = 300000; // 5 minutes
    private final ConcurrentHashMap<String, RateLimitInfo> rateLimitMap = new ConcurrentHashMap<>();

    private final JwtTokenUtil jwtTokenUtil;
    private Set<String> allowedDomainsSet;

    @Value("${app.security.access-token.max-age:900}") // 15 minutes default
    private int accessTokenMaxAge;

    @Value("${app.security.refresh-token.max-age:604800}") // 7 days default
    private int refreshTokenMaxAge;

    @Value("${app.security.cookie.allowed-domains:https://stz-frontend-service-ts.vercel.app,http://localhost:5173,https://vinayak.studeaze.in,https://seller.studeaze.in,https://studeaze.in,https://www.studeaze.in,https://gokuforever.github.io,https://studeaze.retool.com}")
    private String allowedDomainsConfig;

    @Value("${app.security.cookie.same-site:Strict}")
    private String cookieSameSite;

    @Value("${app.security.headers.user-id:req_user_id}")
    private String userIdHeader;

    // Constructor injection
    public PortalServiceInterceptor(JwtTokenUtil jwtTokenUtil) {
        this.jwtTokenUtil = jwtTokenUtil;
    }

    @PostConstruct
    private void initializeAllowedDomains() {
        if (StringUtils.hasText(allowedDomainsConfig)) {
            this.allowedDomainsSet = Arrays.stream(allowedDomainsConfig.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(this::normalizeDomain)
                    .collect(Collectors.toSet());
        } else {
            // Default allowed domains if not configured
            this.allowedDomainsSet = Set.of(
                    "https://stz-frontend-service-ts.vercel.app",
                    "http://localhost:5173",
                    "https://vinayak.studeaze.in",
                    "https://seller.studeaze.in",
                    "https://studeaze.in",
                    "https://www.studeaze.in",
                    "https://gokuforever.github.io",
                    "https://studeaze.retool.com"
            );
        }
        logger.info("Initialized allowed domains: {}", allowedDomainsSet);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler)
            throws Exception {

        String requestUri = request.getRequestURI();
        String origin = request.getHeader("Origin");
        String userIdFromHeader = request.getHeader(userIdHeader);
        String clientIp = getClientIpAddress(request);

        // Check rate limiting
        if (isRateLimited(clientIp)) {
            logger.warn("Rate limit exceeded for IP: {} on URI: {}", clientIp, requestUri);
            generateUnauthorizedResponse(response, "Too many failed attempts. Please try again later.");
            return false;
        }

//        // Validate origin against allowed domains
//        if (!StringUtils.hasText(origin)) {
//            logger.warn("Request from unauthorized origin: {} for URI: {}", origin, requestUri);
//            recordFailedAttempt(clientIp);
//            generateUnauthorizedResponse(response, "Unauthorized origin");
//            return false;
//        }
//        if (!isOriginAllowed(origin)) {
//            logger.warn("Request from unauthorized origin: {} for URI: {}", origin, requestUri);
//            recordFailedAttempt(clientIp);
//            generateUnauthorizedResponse(response, "Unauthorized origin");
//            return false;
//        }

        // Validate required header
        if (!StringUtils.hasText(userIdFromHeader)) {
            logger.warn("Missing {} header for request: {}", userIdHeader, requestUri);
            recordFailedAttempt(clientIp);
            generateUnauthorizedResponse(response, "Missing user ID header");
            return false;
        }

        // Extract tokens from cookies
        TokenPair tokens = extractTokensFromCookies(request);

        if (tokens.isEmpty()) {
            logger.warn("No authentication tokens found for user: {} on request: {}", userIdFromHeader, requestUri);
            recordFailedAttempt(clientIp);
            generateUnauthorizedResponse(response, "Authentication required");
            return false;
        }

        // Try to authenticate with access token first
        if (tokens.hasAccessToken()) {
            AuthResult accessResult = validateToken(tokens.accessToken, userIdFromHeader, "access");
            if (accessResult.isValid() && !accessResult.isExpired()) {
                logger.debug("Successfully authenticated user: {} with access token", userIdFromHeader);
                clearFailedAttempts(clientIp);
                return true;
            }

            if (!accessResult.isValid()) {
                logger.warn("Invalid access token for user: {}", userIdFromHeader);
                recordFailedAttempt(clientIp);
                generateUnauthorizedResponse(response, "Invalid access token");
                return false;
            }

            // Access token expired, try refresh token
            logger.debug("Access token expired for user: {}, attempting refresh", userIdFromHeader);
        }

        // Validate and use refresh token
        if (tokens.lacksRefreshToken()) {
            logger.warn("No refresh token available for user: {}", userIdFromHeader);
            recordFailedAttempt(clientIp);
            generateUnauthorizedResponse(response, "Authentication expired");
            return false;
        }

        AuthResult refreshResult = validateToken(tokens.refreshToken, userIdFromHeader, "refresh");
        if (!refreshResult.isValid()) {
            logger.warn("Invalid refresh token for user: {}", userIdFromHeader);
            recordFailedAttempt(clientIp);
            generateUnauthorizedResponse(response, "Invalid refresh token");
            return false;
        }

        if (refreshResult.isExpired()) {
            logger.warn("Refresh token expired for user: {}", userIdFromHeader);
            recordFailedAttempt(clientIp);
            generateUnauthorizedResponse(response, "Session expired");
            return false;
        }

        // Generate new token pair and set cookies
        try {
            String[] newTokens = jwtTokenUtil.generateToken(userIdFromHeader);
            if (newTokens == null || newTokens.length < 2) {
                logger.error("Invalid token generation result for user: {}", userIdFromHeader);
                recordFailedAttempt(clientIp);
                generateUnauthorizedResponse(response, "Token generation failed");
                return false;
            }
            setAuthenticationCookies(response, newTokens[0], newTokens[1], request);
            logger.info("Successfully refreshed tokens for user: {}", userIdFromHeader);
            clearFailedAttempts(clientIp);
            return true;
        } catch (Exception e) {
            logger.error("Failed to generate new tokens for user: {}", userIdFromHeader, e);
            recordFailedAttempt(clientIp);
            generateUnauthorizedResponse(response, "Token generation failed");
            return false;
        }
    }

    private String normalizeDomain(String domain) {
        if (!StringUtils.hasText(domain)) {
            return domain;
        }

        String normalized = domain.toLowerCase().trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private boolean isOriginAllowed(String origin) {
        if (!StringUtils.hasText(origin)) {
            return false;
        }

        // Validate against malicious schemes
        String lowerOrigin = origin.toLowerCase();
        if (lowerOrigin.startsWith("data:") || lowerOrigin.startsWith("javascript:") ||
                lowerOrigin.startsWith("vbscript:") || lowerOrigin.startsWith("file:")) {
            logger.warn("Potentially malicious origin scheme detected: {}", origin);
            return false;
        }

        String normalizedOrigin = normalizeDomain(origin);
        return allowedDomainsSet.contains(normalizedOrigin);
    }

    private TokenPair extractTokensFromCookies(HttpServletRequest request) {
        String accessToken = null;
        String refreshToken = null;

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                switch (cookie.getName()) {
                    case ACCESS_TOKEN_COOKIE:
                        accessToken = cookie.getValue();
                        break;
                    case REFRESH_TOKEN_COOKIE:
                        refreshToken = cookie.getValue();
                        break;
                }
            }
        }

        return new TokenPair(accessToken, refreshToken);
    }

    private AuthResult validateToken(String token, String expectedUserId, String tokenType) {
        try {
            String extractedUserId = jwtTokenUtil.extractUserId(token);
            boolean isValidUser = expectedUserId.equals(extractedUserId);
            boolean isExpired = jwtTokenUtil.isTokenExpired(token);

            if (!isValidUser) {
                logger.warn("User ID mismatch in {} token. Expected: {}, Found: {}",
                        tokenType, expectedUserId, extractedUserId);
            }

            return new AuthResult(isValidUser, isExpired);
        } catch (Exception e) {
            logger.warn("Failed to validate {} token for user: {}", tokenType, expectedUserId, e);
            return new AuthResult(false, true);
        }
    }

    private void setAuthenticationCookies(HttpServletResponse response, String accessToken, String refreshToken, HttpServletRequest request) {
        // Set access token cookie
        Cookie accessCookie = createSecureCookie(ACCESS_TOKEN_COOKIE, accessToken, accessTokenMaxAge, request);
        response.addCookie(accessCookie);

        // Set refresh token cookie
        Cookie refreshCookie = createSecureCookie(REFRESH_TOKEN_COOKIE, refreshToken, refreshTokenMaxAge, request);
        response.addCookie(refreshCookie);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For can contain multiple IPs, get the first one
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    private boolean isRateLimited(String clientIp) {
        RateLimitInfo rateLimitInfo = rateLimitMap.get(clientIp);
        if (rateLimitInfo == null) {
            return false;
        }

        long currentTime = System.currentTimeMillis();

        // Clean up expired entries
        if (currentTime - rateLimitInfo.getWindowStart() > RATE_LIMIT_WINDOW_MS) {
            rateLimitMap.remove(clientIp);
            return false;
        }

        return rateLimitInfo.getAttempts().get() >= MAX_FAILED_ATTEMPTS;
    }

    private void recordFailedAttempt(String clientIp) {
        long currentTime = System.currentTimeMillis();

        rateLimitMap.compute(clientIp, (key, existing) -> {
            if (existing == null || currentTime - existing.getWindowStart() > RATE_LIMIT_WINDOW_MS) {
                return new RateLimitInfo(currentTime, new AtomicInteger(1));
            } else {
                existing.getAttempts().incrementAndGet();
                return existing;
            }
        });
    }

    private void clearFailedAttempts(String clientIp) {
        rateLimitMap.remove(clientIp);
    }

    private void generateUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String jsonResponse = String.format(
                "{\"error\": \"Unauthorized\", \"message\": \"%s\", \"timestamp\": \"%s\"}",
                message,
                java.time.Instant.now().toString()
        );
        response.getWriter().write(jsonResponse);
    }

    // Helper classes for better code organization
    private record TokenPair(String accessToken, String refreshToken) {

        public boolean hasAccessToken() {
            return StringUtils.hasText(accessToken);
        }

        public boolean hasRefreshToken() {
            return StringUtils.hasText(refreshToken);
        }

        public boolean lacksRefreshToken() {
            return !StringUtils.hasText(refreshToken);
        }

        public boolean isEmpty() {
            return !hasAccessToken() && !hasRefreshToken();
        }
    }

    @Getter
    private static class AuthResult {
        private final boolean valid;
        private final boolean expired;

        public AuthResult(boolean valid, boolean expired) {
            this.valid = valid;
            this.expired = expired;
        }
    }

    @Getter
    private static class RateLimitInfo {
        private final long windowStart;
        private final AtomicInteger attempts;

        public RateLimitInfo(long windowStart, AtomicInteger attempts) {
            this.windowStart = windowStart;
            this.attempts = attempts;
        }
    }
}