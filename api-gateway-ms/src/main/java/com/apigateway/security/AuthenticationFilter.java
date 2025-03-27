package com.apigateway.security;

//import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
//import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.apigateway.util.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.ExpiredJwtException;
//import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

	private static Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);

	public AuthenticationFilter() {
        super(Config.class);
	}

	@Autowired
	private RouterValidator routerValidator;
	@Autowired
	private JwtUtil jwtUtil;

	@Override
	public GatewayFilter apply(Config config) {
		return ((exchange, chain) -> {
			log.info("API Gateway: {}", exchange.getRequest().getPath().toString());

			if (routerValidator.isSecured.test(exchange.getRequest())) {
				if (!exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
					// throw new RuntimeException("Missing Authorisation Header");
					return onError(exchange, "Unauthorized access", HttpStatus.UNAUTHORIZED);
				}

				String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION); //.get(0);
				log.info("Header token: {}", authHeader);
				if (authHeader != null && authHeader.startsWith("Bearer ")) {
					authHeader = authHeader.substring(7);
				}
				try {
					if (!jwtUtil.validateToken(authHeader)) {
						return onError(exchange, "Token Expired", HttpStatus.UNAUTHORIZED);
					}
				} catch (ExpiredJwtException e) {
					return onError(exchange, "Token Expired", HttpStatus.UNAUTHORIZED);
				} catch (Exception ex) {
					ex.printStackTrace();
					log.error("Error Validating Authentication Header: {}", ex.getMessage());
					return onError(exchange, "Invalid Token", HttpStatus.UNAUTHORIZED);
				}
			}

			return chain.filter(exchange);
		});
	}

	private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus status) {
		ServerHttpResponse response = exchange.getResponse();
		response.setStatusCode(status);
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

		Map<String, Object> responseBody = new HashMap<>();
		responseBody.put("timestamp", new Date());
		responseBody.put("status", status.value());
		responseBody.put("error", status.getReasonPhrase());
		responseBody.put("message", err);
		responseBody.put("path", exchange.getRequest().getPath().value());

		try {
			byte[] responseBytes = new ObjectMapper().writeValueAsBytes(responseBody);
			return response.writeWith(Mono.just(response.bufferFactory().wrap(responseBytes)));
		} catch (JsonProcessingException e) {
			log.error("Error writing JSON response", e);
			return response.setComplete();
		}

		// return response.setComplete();
	}

	public static class Config {
	}
}
