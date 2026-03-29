package com.sollite.user.controller;

import com.sollite.global.util.AuthUtil;
import com.sollite.user.domain.enums.ThemeType;
import com.sollite.user.dto.*;
import com.sollite.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 현재 로그인한 사용자 정보 관련 API 컨트롤러.
 * 프로필 조회, 프로필 수정, 비밀번호 변경 등을 처리합니다.
 */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 현재 사용자의 프로필 정보를 조회합니다.
     *
     * @param authentication 현재 인증된 사용자 정보
     * @return 200 OK - 사용자 프로필 정보
     */
    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        Long userId = AuthUtil.getUserId(authentication);
        UserProfileResponse response = userService.getProfile(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 현재 사용자의 프로필 정보를 수정합니다.
     *
     * @param authentication 현재 인증된 사용자 정보
     * @param request 수정할 프로필 정보 (이름, 전화번호 등)
     * @return 200 OK - 수정된 프로필 정보와 메시지
     */
    @PatchMapping
    public ResponseEntity<ProfileUpdateResponse> updateProfile(Authentication authentication,
                                                               @Valid @RequestBody ProfileUpdateRequest request) {
        Long userId = AuthUtil.getUserId(authentication);
        UserProfileResponse user = userService.updateProfile(userId, request);
        return ResponseEntity.ok(new ProfileUpdateResponse(
                "정보가 수정되었습니다.",
                user
        ));
    }

    /**
     * 현재 사용자의 비밀번호를 변경합니다.
     *
     * @param authentication 현재 인증된 사용자 정보
     * @param request 현재 비밀번호와 새 비밀번호
     * @return 200 OK - 변경 완료 메시지
     * @throws BusinessException 비밀번호 불일치 시
     */
    @PatchMapping("/password")
    public ResponseEntity<MessageResponse> changePassword(Authentication authentication,
                                                          @Valid @RequestBody PasswordChangeRequest request) {
        Long userId = AuthUtil.getUserId(authentication);
        userService.changePassword(userId, request);
        return ResponseEntity.ok(new MessageResponse("비밀번호가 변경되었습니다."));
    }

    /**
     * 현재 사용자의 화면 테마를 변경합니다.
     *
     * @param authentication 현재 인증된 사용자 정보
     * @param request 변경할 테마 (LIGHT 또는 DARK)
     * @return 200 OK - 변경된 테마 값 포함 응답
     */
    @PatchMapping("/theme")
    public ResponseEntity<ThemeUpdateResponse> updateTheme(Authentication authentication,
                                                           @Valid @RequestBody ThemeUpdateRequest request) {
        Long userId = AuthUtil.getUserId(authentication);
        ThemeType theme = userService.updateTheme(userId, request);
        return ResponseEntity.ok(new ThemeUpdateResponse("테마가 변경되었습니다.", theme));
    }

}
