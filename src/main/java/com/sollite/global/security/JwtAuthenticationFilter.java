package com.sollite.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 토큰을 검증하고 요청자의 인증 정보를 Spring Security에 등록하는 필터.
 * 모든 HTTP 요청에서 Authorization 헤더의 JWT 토큰을 추출하여 유효성을 확인하고,
 * 유효한 경우 userId를 SecurityContext에 저장합니다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;

    /**
     * 요청의 JWT 토큰을 검증하고 인증 정보를 설정합니다.
     * 유효한 토큰이 있으면 userId를 principal로 하는 Authentication 객체를 생성하여
     * SecurityContext에 저장하므로, Controller에서 Authentication 파라미터로 접근 가능합니다.
     *
     * @param request HTTP 요청 (Authorization 헤더에서 토큰 추출)
     * @param response HTTP 응답
     * @param filterChain 다음 필터 체인
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token)
                && jwtTokenProvider.validateToken(token)
                && !isBlacklisted(token)) {
            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * HTTP 요청의 Authorization 헤더에서 JWT 토큰을 추출합니다.
     * "Bearer {token}" 형식에서 토큰 부분만 반환합니다.
     *
     * @param request HTTP 요청
     * @return JWT 토큰 (없거나 형식이 잘못된 경우 null)
     */
    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    private boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + token));
    }
}
