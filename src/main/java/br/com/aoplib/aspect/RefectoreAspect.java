package br.com.aoplib.aspect;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import br.com.aoplib.aspect.logs.LogMask;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class RefectoreAspect {

    private final ObjectMapper mapper;
    private final AspectProperties properties;

    private static final Set<Class<?>> SIMPLE_FIELD_TYPES = Set.of(
            String.class,
            Long.class,
            Integer.class,
            Short.class,
            Byte.class,
            Double.class,
            Float.class,
            Character.class,
            Boolean.class,
            Date.class,
            LocalDate.class,
            LocalDateTime.class,
            Instant.class,
            BigDecimal.class
    );

    public void logMethodCall(JoinPoint point, Logger log, Class<?> declaringType, Method method) {
        final var argsCrypt = getArgsCrypt(point, true);

        log.info(
                String.format("%s@%s - Input - %s",
                        declaringType.getSimpleName(),
                        method.getName(),
                        toJsonNode(argsCrypt)
                )
        );
    }

    public List<JsonNode> getArgsCrypt(JoinPoint point, boolean possibleMask) {
        final var argsWithoutExceptions = Arrays.stream(point.getArgs())
                .filter(this::isNotExceptionOrValidatorContext)
                .toList();

        return argsWithoutExceptions.stream()
                .map(arg -> toJsonNode(arg, possibleMask))
                .collect(Collectors.toList());
    }

    protected JsonNode toJsonNode(Object object) {
        return mapper.valueToTree(object);
    }

    public String toJsonString(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    public JsonNode toJsonNode(Object arg, boolean possibleMask) {
        if (arg == null) {
            return null;
        }

        if (isSimpleField(arg)) {
            return shouldMask(arg.getClass().getAnnotations(), possibleMask) ? getNodeEncrypt() : toJsonNode(arg);
        }

        return switch (arg) {
            case Optional<?> optional -> optional.map(obj -> toJsonNode(obj, possibleMask)).orElse(null);
            case Iterable<?> iterable -> toJsonNode(
                    StreamSupport.stream(iterable.spliterator(), false)
                            .map(obj -> toJsonNode(obj, possibleMask))
                            .collect(Collectors.toList())
            );
            case Map<?, ?> map -> toJsonNode(map); //fixme
            case ResponseEntity<?> responseEntity -> toJsonNode(responseEntity.getBody(), possibleMask);
            default -> toJsonNodeWithFields(arg, possibleMask);
        };
    }

    private boolean isSimpleField(Object object) {
        final var className = object.getClass();
        return SIMPLE_FIELD_TYPES.contains(className) || className.isEnum() || className.isArray();
    }

    private boolean shouldMask(Annotation[] annotations, boolean possibleMask) {
        return possibleMask && Arrays.stream(annotations).anyMatch(LogMask.class::isInstance);
    }

    private static TextNode getNodeEncrypt() {
        return new TextNode("***");
    }

    private JsonNode toJsonNodeWithFields(Object arg, boolean possibleMask) {
        Class<?> argClass = arg.getClass();
        var declaredFields = getDeclaredFields(argClass);
        var fields = new HashMap<String, Object>();

        declaredFields.forEach(field -> addFields(arg, possibleMask, field, fields));

        return toJsonNode(fields);
    }

    private void addFields(Object arg, boolean possibleMask, Field field, HashMap<String, Object> fields) {
        field.setAccessible(true);
        try {
            fields.put(field.getName(), shouldMask(field.getAnnotations(), possibleMask)
                    ? getNodeEncrypt() : field.get(arg));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Field> getDeclaredFields(Class<?> aClass) {
        List<Class<?>> classHierarchy = new ArrayList<>();
        classHierarchy.add(aClass);
        for (int i = 1; i <= properties.getExtensionsLevel(); i++) {
            Class<?> superClass = classHierarchy.getLast().getSuperclass();
            if (superClass.equals(Object.class)) {
                break;
            }
            classHierarchy.add(superClass);
        }

        return classHierarchy.stream()
                .flatMap(clazz -> Arrays.stream(clazz.getDeclaredFields()))
                .collect(Collectors.toList());
    }

    private boolean isNotExceptionOrValidatorContext(Object object) {
        return !(object instanceof Exception) && !(object instanceof ConstraintValidatorContext);
    }

}