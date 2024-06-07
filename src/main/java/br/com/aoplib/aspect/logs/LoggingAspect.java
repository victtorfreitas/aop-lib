package br.com.aoplib.aspect.logs;

import br.com.aoplib.aspect.errors.dto.ErrorGenericQueue;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import br.com.aoplib.aspect.AspectProperties;
import br.com.aoplib.aspect.RefectoreAspect;
import br.com.aoplib.aspect.exceptions.ExclusionRuleException;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoggingAspect {

    private final RefectoreAspect refectoreAspect;
    private final AspectProperties properties;

    @Value("${spring.application.name}")
    private String applicationName;


    public void loggerIn(JoinPoint point) {
        try {
            final var loggerDetails = getLoggerDetails(point.getSignature());
            final var correlationId = MDC.get(properties.getCorrelationName());
            if (hasInputCorrelationId(loggerDetails.getMethod()) && correlationId == null) {
                MDC.put(properties.getCorrelationName(), UUID.randomUUID().toString());
            }
            processLogger(point, loggerDetails);
        } catch (ExclusionRuleException ignored) {
        }
    }

    public void loggerOut(JoinPoint point, Object result) {
        try {
            final var loggerDetails = getLoggerDetails(point.getSignature());
            processLoggerOut(result, loggerDetails);
            if (hasInputCorrelationId(loggerDetails.getMethod())) {
                MDC.remove(properties.getCorrelationName());
            }
        } catch (ExclusionRuleException ignored) {
        }
    }

    public ErrorGenericQueue loggerError(JoinPoint point, Throwable error, String correlationId, LoggerDetails loggerDetails) {
        try {
            final var argsCrypt = refectoreAspect.getArgsCrypt(point, false);

            Map<String, JsonNode> params = new HashMap<>();
            int bound = loggerDetails.getMethod().getParameters().length;

            for (int i = 0; i < bound; i++) {
                params.put(loggerDetails.getMethod().getParameters()[i].getName(), argsCrypt.get(i));
            }

            final var errorGenericQueue = ErrorGenericQueue.builder()
                    .applicationName(applicationName)
                    .message(error.getLocalizedMessage())
                    .correlationId(correlationId)
                    .dateTime(LocalDateTime.now())
                    .params(params)
                    .method(loggerDetails.getMethod().toString())
                    .stacktrace(error.getStackTrace()[0].toString())
                    .build();

            loggerDetails.getLog().error(
                    String.format("%s@%s - Output - %s",
                            loggerDetails.getDeclaringType().getSimpleName(),
                            loggerDetails.getMethod().getName(),
                            refectoreAspect.toJsonString(errorGenericQueue)
                    ), error);
            return errorGenericQueue;
        } catch (RuntimeException ex) {
            loggerDetails.getLog().error("Error during intercept logger {}", ex.getLocalizedMessage());
        }
        return null;
    }

    private void processLogger(JoinPoint point, LoggerDetails loggerDetails) {
        try {
            refectoreAspect.logMethodCall(point, loggerDetails.getLog(), loggerDetails.getDeclaringType(), loggerDetails.getMethod());
        } catch (RuntimeException ex) {
            loggerDetails.getLog().error("Error during intercept logger {}", ex.getLocalizedMessage());
        }
    }

    private boolean isExclusionsRules(Signature signature) {
        return properties.getExclusions().getPackages().stream().anyMatch(pack -> signature.getDeclaringTypeName().contains(pack)) ||
                Arrays.stream(properties.getExclusions().getMethods()).anyMatch(method -> signature.getName().equals(method));
    }

    private void processLoggerOut(Object result, LoggerDetails loggerDetails) {
        try {
            loggerDetails.getMethod().setAccessible(true);

            loggerDetails.getLog().info(
                    String.format("%s@%s - Output - %s",
                            loggerDetails.getDeclaringType().getSimpleName(),
                            loggerDetails.getMethod().getName(),
                            refectoreAspect.toJsonNode(result, true)
                    )
            );
        } catch (RuntimeException ex) {
            loggerDetails.getLog().error("Error during intercept logger {}", ex.getLocalizedMessage());
        }
    }

    private LoggerDetails getLoggerDetails(Signature signature) throws ExclusionRuleException {

        if (isExclusionsRules(signature))
            throw new ExclusionRuleException();

        return getDetails(signature);
    }

    public LoggerDetails getDetails(Signature signature) {
        Class<?> declaringType = (Class<?>) signature.getDeclaringType();

        final Logger log = LoggerFactory.getLogger(declaringType);

        final Method method = ((MethodSignature) signature).getMethod();

        return LoggerDetails.builder()
                .declaringType(declaringType)
                .log(log)
                .method(method)
                .build();
    }

    private boolean hasInputCorrelationId(Method method) {
        return method.getAnnotation(AsyncCorrelationId.class) != null;
    }
}
