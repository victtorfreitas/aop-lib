package br.com.aoplib.aspect;


import br.com.aoplib.aspect.errors.handle.ErrorMessageHandle;
import br.com.aoplib.aspect.errors.handle.ExceptionHandler;
import br.com.aoplib.aspect.logs.LoggingAspect;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;


@Aspect
@Component
@RequiredArgsConstructor
public class AspectConfig {
    private final LoggingAspect loggingAspect;
    private final ErrorMessageHandle errorMessageHandle;
    private final AspectProperties properties;

    @Pointcut("!execution(* *.*.*.config..*.*(..)) && execution(* br.*.*.*..*.*(..)) ")
    public void pointCut() {
    }

    @Before(value = "pointCut()")
    public void logMethodBefore(JoinPoint point) {
        loggingAspect.loggerIn(point);
    }


    @AfterReturning(pointcut = "pointCut()", returning = "result")
    public void logMethodAfter(JoinPoint point, Object result) {
        loggingAspect.loggerOut(point, result);
    }

    @AfterThrowing(pointcut = "pointCut()", throwing = "error")
    public void afterThrowingAdvice(JoinPoint point, Throwable error) {
        final var correlationId = MDC.get(properties.getCorrelationName());
        final var loggerDetails = loggingAspect.getDetails(point.getSignature());

        CompletableFuture.runAsync(() -> {
            MDC.put(properties.getCorrelationName(), correlationId);
            final var errorGenericQueue = loggingAspect.loggerError(point, error, correlationId, loggerDetails);
            if (properties.getError().isEnableHandle() && hasAnnotation(loggerDetails.getMethod())) {
                errorMessageHandle.process(errorGenericQueue);
            }
        });
    }

    private boolean hasAnnotation(Method method) {
        return method.getAnnotation(ExceptionHandler.class) != null;
    }


}
