package com.sollite.global.util;

import org.springframework.security.core.Authentication;

public class AuthUtil {

    private AuthUtil() {}

    /**
     * Authentication 객체에서 사용자 ID를 추출합니다.
     * JwtAuthenticationFilter에서는 principal이 Long, @WithMockUser에서는 getName()으로 처리합니다.
     *
     * @param authentication 현재 인증된 사용자 정보
     * @return 사용자 ID
     */
    public static Long getUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long id) {
            return id;
        }
        return Long.parseLong(authentication.getName());
    }
}
