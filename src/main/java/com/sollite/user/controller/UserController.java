package com.sollite.user.controller;

import com.sollite.user.dto.PasswordChangeRequest;
import com.sollite.user.dto.ProfileUpdateRequest;
import com.sollite.user.dto.UserProfileResponse;
import com.sollite.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        Long userId = getUserId(authentication);
        UserProfileResponse response = userService.getProfile(userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping
    public ResponseEntity<Map<String, Object>> updateProfile(Authentication authentication,
                                                              @Valid @RequestBody ProfileUpdateRequest request) {
        Long userId = getUserId(authentication);
        UserProfileResponse user = userService.updateProfile(userId, request);
        return ResponseEntity.ok(Map.of(
                "message", "정보가 수정되었습니다.",
                "user", user
        ));
    }

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
