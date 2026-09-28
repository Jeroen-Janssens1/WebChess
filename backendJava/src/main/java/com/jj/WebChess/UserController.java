package com.jj.WebChess;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private UserEntity getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("Authentication required");
        }

        return userRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new SecurityException("User not found"));
    }

    @GetMapping("user")
    public ResponseEntity<Object> getCurrentUserInfo() {
        try {
            UserEntity user = getCurrentUser();
            return ResponseEntity.ok(Map.of("user", Map.of("username", user.getUsername())));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
    }

    @DeleteMapping("user")
    public ResponseEntity<Object> deleteCurrentUser() {
        try {
            UserEntity user = getCurrentUser();
            userRepository.delete(user);
            SecurityContextHolder.clearContext();
            return ResponseEntity.ok(Map.of("message", "User account deleted successfully"));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
    }
}
