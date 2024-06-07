package br.com.aoplib.aspect.logs;

import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;

import java.lang.reflect.Method;

@Data
@Builder
public class LoggerDetails {
    private Method method;
    private Logger log;
    private Class<?> declaringType;
}
