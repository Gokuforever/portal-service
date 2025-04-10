package com.sorted.portal.security;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import com.sorted.commons.jwt.JwtTokenUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class PortalServiceInterceptor implements HandlerInterceptor {

	@Autowired
	private JwtTokenUtil jwtTokenUtil;

	@Override
	public boolean preHandle(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler)
			throws Exception {
		String token = request.getHeader("token");
		String req_user_id = request.getHeader("req_user_id");
		if (!StringUtils.hasText(token) || !StringUtils.hasText(req_user_id)) {
			this.generateUnauthorized(response);
			return false;
		}
		String extractUserId = jwtTokenUtil.extractUserId(token);
		if (!req_user_id.equals(extractUserId)) {
			this.generateUnauthorized(response);
			return false;
		}
		Boolean expired = jwtTokenUtil.isTokenExpired(token);
		if (Boolean.TRUE.equals(expired)) {
			this.generateUnauthorized(response);
			return false;
		}
		return true;
	}

	private void generateUnauthorized(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType("application/json");
		response.getWriter().write("{\"message\": \"Unauthorized.\"}");
	}

}
