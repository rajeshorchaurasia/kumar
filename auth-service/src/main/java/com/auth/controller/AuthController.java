package com.auth.controller;

import java.security.SignatureException;
import java.util.Collections;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import com.auth.dto.AuthRequest;
import com.auth.entity.UserInfo;
import com.auth.repository.UserInfoRepository;
import com.auth.service.JwtService;
import com.auth.util.Utility;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private UserInfoRepository userInfoRepository;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final WebClient webClient;

    // Constructor to initialize WebClient
    public AuthController(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://localhost:8082/email").build();
    }
    
    
    @GetMapping("/welcome")
    public String welcome() {
        return "Welcome to Auto-Service";
    }

//    @PostMapping("/register")
//    public ResponseEntity<UserInfo> addNewUser(@RequestBody UserInfo userInfo) {
//        userInfo.setPassword(passwordEncoder.encode(userInfo.getPassword()));
//        UserInfo userInfoDb = userInfoRepository.save(userInfo);
//        return ResponseEntity.status(HttpStatus.CREATED).body(userInfoDb);
//    }
    @PostMapping("/register")
    public ResponseEntity<String> addNewUser(@RequestBody UserInfo userInfo) {
        // Encrypt password before saving
        userInfo.setPassword(passwordEncoder.encode(userInfo.getPassword()));
        
        // Generate OTP
        String otp = Utility.generateOTP();
        
        // Save user info with OTP
        userInfo.setOtp(otp);
        userInfoRepository.save(userInfo);
        
        // Send OTP via email
        Mono<String> emailSent = sendOtpEmail(userInfo.getEmail(), otp);
        if (emailSent.blockOptional().get() == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error sending OTP email. Please try again.");
        }
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("User registered successfully. Please check your email for OTP.");
    }

    
	private Mono<String> sendOtpEmail(String toEmail, String otp) {
		try {
			return webClient.post()
					.uri(uriBuilder -> uriBuilder.path("/send").queryParam("to", toEmail)
							.queryParam("subject", "Your OTP for Account Activation")
							.queryParam("text", "Your OTP is: " + otp + "\nPlease use this to activate your account.")
							.build())
					.retrieve().bodyToMono(String.class);

		} catch (Exception e) {
			e.printStackTrace(); // Log the error for debugging
			return null;
		}
	}


	@PostMapping("/authenticate") // login
    public ResponseEntity<?> authenticateAndGetToken(@RequestBody AuthRequest authRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getUsername(), authRequest.getPassword()));

            if (authentication.isAuthenticated()) {
                String token = jwtService.generateToken(authRequest.getUsername());
                return ResponseEntity.ok(Collections.singletonMap("token", token)); // Returning JSON response
            } else {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user request!");
            }
        } catch (Exception ex) {
        	throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials!", ex);
        }
    }

    @GetMapping("/validate/{token}")
	public ResponseEntity<Map<String, String>> validateTokenAndGetClaims(@PathVariable String token) throws SignatureException {
		try {
			if (jwtService.validateToken(token)) {
				return ResponseEntity.ok(Collections.singletonMap("Valid_Token", token));
			}
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
		} catch (ExpiredJwtException ex) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT token has expired", ex);
		} catch (MalformedJwtException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed JWT token", ex);
		}
	}


}
