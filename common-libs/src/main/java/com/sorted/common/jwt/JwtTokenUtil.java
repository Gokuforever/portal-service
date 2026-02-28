package com.sorted.common.jwt;

import com.sorted.common.entity.mongo.User_Auth_Details;
import com.sorted.common.entity.service.User_Auth_Details_Service;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtTokenUtil {

	@Autowired
	private User_Auth_Details_Service user_Auth_Details_Service;

	private final LocalDateTime expiry_time = LocalDateTime.now().minusMinutes(15);

	// Use a key of 256 bits (32 characters) for HMAC-SHA
	private final String SECRET_KEY_STRING = "your_very_long_secret_key_here_12345";
	private final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET_KEY_STRING.getBytes());

	@Value("${se.portal.jwt.access_token.expiration.in.minutes:15}")
	private long accessTokenExpirationMinutes;

	@Value("${se.portal.jwt.refresh_token.expiration.in.minutes:43200}")
	private long refreshTokenExpirationMinutes;

	public String[] generateToken(String userId) {
		String accessToken = generateAccessToken(userId);
		String refreshToken = generateRefreshToken(userId);
		LocalDateTime now = LocalDateTime.now();
		SEFilter filterUAD = new SEFilter(SEFilterType.AND);
		filterUAD.addClause(WhereClause.eq(User_Auth_Details.Fields.req_user_id, userId));
		filterUAD.addClause(WhereClause.gt(User_Auth_Details.Fields.expiry_datetime, expiry_time));

		List<User_Auth_Details> listUAD = user_Auth_Details_Service.repoFind(filterUAD);
		if (!CollectionUtils.isEmpty(listUAD)) {
			for (User_Auth_Details user_Auth_Details : listUAD) {
				user_Auth_Details.setExpiry_datetime(now);
				user_Auth_Details_Service.update(user_Auth_Details.getId(), user_Auth_Details, userId);
			}
		}
		User_Auth_Details details = new User_Auth_Details();
		details.setReq_user_id(userId);
		details.setToken(accessToken);
		details.setRefresh_token(refreshToken);
		details.setExpiry_datetime(now.plusMinutes(accessTokenExpirationMinutes));

		user_Auth_Details_Service.create(details, userId);
		return new String[] { accessToken, refreshToken };
	}

	// Generate a token for access (short-lived)
	private String generateAccessToken(String userId) {
		Map<String, Object> claims = new HashMap<>();
		return createToken(claims, userId, accessTokenExpirationMinutes);
	}

	// Generate a refresh token (long-lived)
	private String generateRefreshToken(String userId) {
		Map<String, Object> claims = new HashMap<>();
		return createToken(claims, userId, refreshTokenExpirationMinutes);
	}

	// Create token with expiry
	private String createToken(Map<String, Object> claims, String subject, long expirationMinutes) {
		long expirationMillis = expirationMinutes * 60 * 1000;
		return Jwts.builder().setClaims(claims).setSubject(subject).setIssuedAt(new Date(System.currentTimeMillis()))
				.setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
				.signWith(SECRET_KEY, SignatureAlgorithm.HS256).compact();
	}

	// Validate token
	public Boolean validateToken(String token, String userId) {
		final String extractedUserId = extractUserId(token);
		return (extractedUserId.equals(userId) && !isTokenExpired(token));
	}

	// Extract userId
	public String extractUserId(String token) {
		return extractClaim(token, Claims::getSubject);
	}

	// Check if token is expired
	public Boolean isTokenExpired(String token) {
		return extractExpiration(token).before(new Date());
	}

	private Date extractExpiration(String token) {
		return extractClaim(token, Claims::getExpiration);
	}

	public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
		final Claims claims = extractAllClaims(token);
		return claimsResolver.apply(claims);
	}

	private Claims extractAllClaims(String token) {
		return Jwts.parserBuilder().setSigningKey(SECRET_KEY).build().parseClaimsJws(token).getBody();
	}
}