package com.myroutine.common.logging;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class LogOrderIdAspect {

    @Around("@annotation(com.myroutine.common.logging.LogOrderId)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String[] names = signature.getParameterNames();
        Object[] args = pjp.getArgs();

        for (int i = 0; i < names.length; i++) {
            if (names[i].equals("orderId")) {
                try (MDC.MDCCloseable ignored = MDC.putCloseable("orderId", args[i].toString())) {
                    return pjp.proceed();
                }
            }
        }
        return pjp.proceed();   // orderId 파라미터가 없으면 그냥 실행한다
    }
}
