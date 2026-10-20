package com.myroutine.common.idempotency;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.common.security.AuthClaims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final ObjectMapper objectMapper;
    private final IdempotencyRepository idempotencyRepository;
    private final Clock clock;

    @Around("@annotation(com.myroutine.common.idempotency.Idempotent)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        AuthClaims authClaims = (AuthClaims) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID memberId = authClaims.memberId();
        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isEmpty() || idempotencyKey.length() > 100) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }

        String requestHash = calculateHash(request, pjp);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofHours(24));
        if (!idempotencyRepository.claim(memberId, idempotencyKey, requestHash, now, expiresAt)) {
            StoredKey storedKey = idempotencyRepository.find(memberId, idempotencyKey).orElseThrow(
                    () -> new BusinessException(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS)
            );
            if (storedKey.expiresAt().isBefore(now)) {
                idempotencyRepository.deleteExpired(memberId, idempotencyKey, now);
                if (!idempotencyRepository.claim(memberId, idempotencyKey, requestHash, now, expiresAt)) {
                    throw new BusinessException(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS);
                }
            } else if (!storedKey.hash().equals(requestHash)) {
                throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_REUSED);
            } else if (storedKey.status().equals("COMPLETED")) {
                return ResponseEntity.status(storedKey.responseStatus()).body(objectMapper.readTree(storedKey.responseBody()));
            } else {
                throw new BusinessException(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS);
            }
        }

        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            idempotencyRepository.delete(memberId, idempotencyKey);
            throw t;
        }

        if (!(result instanceof ResponseEntity<?> response)) {
            throw new IllegalStateException("@Idempotent 메서드는 ResponseEntity를 반환해야 합니다.");
        }

        if (response.getStatusCode().is2xxSuccessful()) {
            idempotencyRepository.complete(
                    memberId,
                    idempotencyKey,
                    response.getStatusCode().value(),
                    objectMapper.writeValueAsString(response.getBody()),
                    clock.instant()
            );
        } else {
            idempotencyRepository.delete(memberId, idempotencyKey);
        }

        return result;
    }

    private Object findRequestBody(ProceedingJoinPoint pjp) {
        Annotation[][] paramAnnotations = ((MethodSignature) pjp.getSignature()).getMethod().getParameterAnnotations();
        Object[] args = pjp.getArgs();
        for (int i = 0; i < args.length; i++) {
            for (Annotation a : paramAnnotations[i]) {
                if (a instanceof RequestBody) {
                    return args[i];
                }
            }
        }
        return null;
    }

    private String calculateHash(HttpServletRequest request, ProceedingJoinPoint pjp) throws NoSuchAlgorithmException {
        String jsonBody = objectMapper.writeValueAsString(findRequestBody(pjp));
        String source = request.getMethod() + " " + request.getRequestURI() + "\n" + jsonBody;
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

}
