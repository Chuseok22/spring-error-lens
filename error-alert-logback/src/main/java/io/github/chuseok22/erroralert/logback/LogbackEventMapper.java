package io.github.chuseok22.erroralert.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.ErrorLevel;
import io.github.chuseok22.erroralert.core.model.EventType;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Logback ILoggingEvent를 ErrorEvent로 변환한다.
 */
public class LogbackEventMapper {

    private LogbackEventMapper() {}

    public static ErrorEvent map(ILoggingEvent event, String executionContextId) {
        boolean throwableExists = event.getThrowableProxy() != null;
        String exceptionClass = null;
        String exceptionMessage = null;
        String stackTrace = null;

        if (throwableExists) {
            IThrowableProxy proxy = event.getThrowableProxy();
            exceptionClass = proxy.getClassName();
            exceptionMessage = proxy.getMessage();
            stackTrace = buildStackTrace(proxy);
        }

        return ErrorEvent.builder()
                .eventType(EventType.LOG_ERROR)
                .level(toErrorLevel(event.getLevel()))
                .occurredAt(Instant.ofEpochMilli(event.getTimeStamp()))
                .loggerName(event.getLoggerName())
                .threadName(event.getThreadName())
                .message(event.getFormattedMessage())
                .throwableExists(throwableExists)
                .exceptionClass(exceptionClass)
                .exceptionMessage(exceptionMessage)
                .stackTrace(stackTrace)
                .mdc(copyMdc(getMdcSafe(event)))
                .executionContextId(executionContextId)
                .build();
    }

    private static ErrorLevel toErrorLevel(Level level) {
        return level == Level.WARN ? ErrorLevel.WARN : ErrorLevel.ERROR;
    }

    private static Map<String, String> getMdcSafe(ILoggingEvent event) {
        try {
            return event.getMDCPropertyMap();
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, String> copyMdc(Map<String, String> mdc) {
        if (mdc == null || mdc.isEmpty()) return Map.of();
        return new HashMap<>(mdc);
    }

    private static String buildStackTrace(IThrowableProxy proxy) {
        StringBuilder sb = new StringBuilder();
        appendProxy(sb, proxy, "");
        return sb.toString().stripTrailing();
    }

    private static void appendProxy(StringBuilder sb, IThrowableProxy proxy, String prefix) {
        sb.append(prefix)
          .append(proxy.getClassName())
          .append(": ")
          .append(proxy.getMessage())
          .append('\n');

        for (StackTraceElementProxy element : proxy.getStackTraceElementProxyArray()) {
            sb.append("\tat ").append(element.getSTEAsString()).append('\n');
        }

        if (proxy.getCause() != null) {
            sb.append("Caused by: ");
            appendProxy(sb, proxy.getCause(), "");
        }
    }
}
