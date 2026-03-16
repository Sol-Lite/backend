package com.sollite.user.controller;

import com.sollite.user.dto.PasswordChangeRequest;
import com.sollite.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(Authentication authentication,
                                                               @Valid @RequestBody PasswordChangeRequest request) {
        Long userId = getUserId(authentication);
        userService.changePassword(userId, request);
        return ResponseEntity.ok(Map.of("message", "비밀번호가 변경되었습니다."));
    }

    private Long getUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long id) {
            return id;
        }
        return Long.parseLong(authentication.getName());
    }
}
