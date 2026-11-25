package com.myroutine.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final SessionVerifier sessionVerifier;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length());

        jwtProvider.parse(token).flatMap(claims -> sessionVerifier.verify(claims.sessionId(), claims.memberId())).ifPresent(authClaims -> {
            UsernamePasswordAuthenticationToken authenticatedToken = UsernamePasswordAuthenticationToken.authenticated(authClaims, null, List.of(new SimpleGrantedAuthority("ROLE_" + authClaims.role())));
            SecurityContextHolder.getContext().setAuthentication(authenticatedToken);
            MDC.put("memberId", authClaims.memberId().toString());
        });

        filterChain.doFilter(request, response);
    }
}
