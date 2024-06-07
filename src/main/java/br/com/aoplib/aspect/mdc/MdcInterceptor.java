package br.com.aoplib.aspect.mdc;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@RequiredArgsConstructor
public class MdcInterceptor implements HandlerInterceptor {

    private final String correlationName;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        final var header = request.getHeader(correlationName);
        MDC.put(correlationName, header != null && !header.isEmpty() ? header : getUuid());
        return true;

    }

    private String getUuid() {
        return UUID.randomUUID().toString();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        MDC.remove(correlationName);
    }
}