package com.sorted.portal.service;

import com.sorted.commons.beans.UsersBean;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.StringUtils;

import java.net.URI;


@Log4j2
public class CookieService {


    public static void setCookies(HttpServletResponse response, UsersBean usersBean) {
        // Access Token Cookie
        String accessCookie = String.format(
                "access_token=%s; Max-Age=%d; Path=/; Secure; HttpOnly; SameSite=None",
                usersBean.getToken(), 15 * 60
        );
        response.setHeader("Set-Cookie", accessCookie); // First cookie

        // Refresh Token Cookie
        String refreshCookie = String.format(
                "refresh_token=%s; Max-Age=%d; Path=/; Secure; HttpOnly; SameSite=None",
                usersBean.getRefresh_token(), 7 * 24 * 60 * 60
        );
        response.addHeader("Set-Cookie", refreshCookie); // Add second cookie
    }

//    public static void setCookies(HttpServletResponse httpServletResponse, UsersBean usersBean) {
//        Cookie accessCookie = new Cookie("access_token", usersBean.getToken());
//        accessCookie.setHttpOnly(true);
//        accessCookie.setSecure(true);
//        accessCookie.setMaxAge(15 * 60); // 15 minutes
//        accessCookie.setPath("/");
//
//        // Refresh Token Cookie (longer-lived)
//        Cookie refreshCookie = new Cookie("refresh_token", usersBean.getRefresh_token());
//        refreshCookie.setHttpOnly(true);
//        refreshCookie.setSecure(true);
//        refreshCookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
//        refreshCookie.setPath("/");
//
//        httpServletResponse.addCookie(accessCookie);
//        httpServletResponse.addCookie(refreshCookie);
//    }
//
//    public static Cookie createSecureCookie(String name, String value, int maxAge, HttpServletRequest request) {
//        Cookie cookie = new Cookie(name, value);
//        cookie.setHttpOnly(true);
//        cookie.setSecure(true);
//        cookie.setMaxAge(maxAge);
//        cookie.setPath("/");
//
//        // Set domain based on request origin if it matches allowed domains
//        String cookieDomain = determineCookieDomain(request);
//        if (StringUtils.hasText(cookieDomain)) {
//            cookie.setDomain(cookieDomain);
//        }
//
//        // Note: SameSite attribute would need to be set via response headers
//        // as Cookie class doesn't directly support it in older versions
//
//        return cookie;
//    }

    public static void createSecureCookie(String name, String value, int maxAge, HttpServletResponse response) {
        // Access Token Cookie
        String cookie = String.format(
                "%s=%s; Max-Age=%d; Path=/; Secure; HttpOnly; SameSite=None",
                name, value, maxAge
        );
        response.setHeader("Set-Cookie", cookie); // First cookie
    }

    private static String determineCookieDomain(HttpServletRequest request) {
        String origin = request.getHeader("Origin");

        if (!StringUtils.hasText(origin)) {
            return null;
        }

        try {
            URI uri = URI.create(origin);
            String host = uri.getHost();

            if (host == null) {
                return null;
            }

            // Explicit domain mapping for studeaze.in subdomains
            if (host.endsWith("studeaze.in")) {
                return ".studeaze.in";
            }

            // Don't set domain for localhost/development environments
            if (host.equals("localhost") || host.startsWith("127.0.0.1") || host.startsWith("192.168.")) {
                return null;
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to parse origin: {}", origin, e);
            return null;
        }
    }
}
