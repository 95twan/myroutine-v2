package com.myroutine.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String traceId = TraceIds.newId();
        MDC.put(TraceIds.MDC_KEY, traceId);
        response.setHeader(TraceIds.HEADER, traceId);

        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info("http {} {} status={} {}ms", request.getMethod(), request.getRequestURI(), response.getStatus(), (System.nanoTime() - start) / 1_000_000);
            MDC.remove(TraceIds.MDC_KEY);
            MDC.remove("memberId");
        }
    }
}
