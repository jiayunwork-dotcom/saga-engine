package com.saga.engine.controller;

import com.saga.engine.dto.*;
import com.saga.engine.entity.User;
import com.saga.engine.enums.UserRole;
import com.saga.engine.repository.UserRepository;
import com.saga.engine.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(401, "Invalid username or password"));
        }

        if (!user.getEnabled()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(403, "User account is disabled"));
        }

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("username", user.getUsername());
        data.put("role", user.getRole());
        data.put("email", user.getEmail());

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        String username = jwtTokenProvider.getUsernameFromToken(token);
        UserRole role = jwtTokenProvider.getRoleFromToken(token);

        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("role", role);

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
