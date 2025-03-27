package com.apigateway.exception;

import java.util.Date;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@Component
@Order(-2) //Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler implements WebExceptionHandler {

	private static final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
		ServerHttpResponse response = exchange.getResponse();

		HttpStatus status = determineHttpStatus(ex);
		String message = determineErrorMessage(ex);

		response.setStatusCode(status);

		Map<String, Object> errorResponse = Map.of("timestamp", new Date(), "status", status.value(), "error",
				status.getReasonPhrase(), "message", message, "path", exchange.getRequest().getPath().value());
		try {
			byte[] responseBytes = objectMapper.writeValueAsBytes(errorResponse);
			return response.writeWith(Mono.just(response.bufferFactory().wrap(responseBytes)));
		} catch (Exception e) {
			return response.setComplete();
		}
	}

	private HttpStatus determineHttpStatus(Throwable ex) {
        if (ex instanceof WebClientResponseException webClientResponseException) {
            return HttpStatus.resolve(webClientResponseException.getStatusCode().value());
        } else if (ex instanceof ResponseStatusException responseStatusException) {
        	return HttpStatus.resolve(responseStatusException.getStatusCode().value());
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
	
	private String determineErrorMessage(Throwable ex) {
        if (ex instanceof WebClientResponseException webClientResponseException) {
            return webClientResponseException.getResponseBodyAsString();
        } else if (ex instanceof ResponseStatusException) {
            return ex.getMessage();
        } else {
            return "Internal server error";
        }
    }
	/*
	 * private HttpStatus determineHttpStatus(Throwable ex) { if (ex instanceof
	 * SignatureException) { return HttpStatus.UNAUTHORIZED; } else if (ex
	 * instanceof ExpiredJwtException) { return HttpStatus.UNAUTHORIZED; } else if
	 * (ex instanceof MalformedJwtException) { return HttpStatus.BAD_REQUEST; } else
	 * if (ex instanceof ResponseStatusException responseStatusException) { return
	 * HttpStatus.resolve(responseStatusException.getStatusCode().value()); } else {
	 * return HttpStatus.INTERNAL_SERVER_ERROR; } }
	 * 
	 * private String determineErrorMessage(Throwable ex) { if (ex instanceof
	 * SignatureException) { return "Invalid JWT signature"; } else if (ex
	 * instanceof ExpiredJwtException) { return "JWT token has expired"; } else if
	 * (ex instanceof MalformedJwtException) { return "Malformed JWT token"; } else
	 * if (ex instanceof ResponseStatusException) { return ex.getMessage(); } else {
	 * return "Internal server error"; } }
	 */

}
