package ru.maltsev.primemarketbackend.logging;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class MethodLoggingAspect {

    @Around(
        "execution(* ru.maltsev.primemarketbackend..*(..))"
            + " && !within(ru.maltsev.primemarketbackend.logging..*)"
            + " && !@within(org.springframework.boot.context.properties.ConfigurationProperties)"
            + " && !within(jakarta.servlet.Filter+)"
    )
    public Object logMethodCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String signature = joinPoint.getSignature().toShortString();
        String args = formatArgs(joinPoint.getArgs());
        long start = System.nanoTime();
        log.info("-> {}({})", signature, args);
        try {
            Object result = joinPoint.proceed();
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.info("<- {} ({} ms)", signature, durationMs);
            return result;
        } catch (Throwable ex) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.warn("<- {} ({} ms) error={}", signature, durationMs, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        return Arrays.stream(args)
            .map(this::formatArg)
            .collect(Collectors.joining(", "));
    }

    private String formatArg(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (arg instanceof Number || arg instanceof Boolean || arg instanceof Enum<?>) {
            return arg.toString();
        }
        switch (arg) {
            case CharSequence sequence -> {
                return "string(len=" + sequence.length() + ")";
            }
            case byte[] bytes -> {
                return "byte[len=" + bytes.length + "]";
            }
            case Collection<?> collection -> {
                return arg.getClass().getSimpleName() + "(size=" + collection.size() + ")";
            }
            case Map<?, ?> map -> {
                return arg.getClass().getSimpleName() + "(size=" + map.size() + ")";
            }
            case Optional<?> optional -> {
                return optional.isPresent() ? "Optional(present)" : "Optional(empty)";
            }
            default -> {
            }
        }
        String className = arg.getClass().getName();
        if (className.startsWith("jakarta.servlet") || className.startsWith("org.springframework")) {
            return arg.getClass().getSimpleName();
        }
        return arg.getClass().getSimpleName();
    }
}
