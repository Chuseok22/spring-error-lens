package io.github.chuseok22.erroralert.autoconfigure.request;

import io.github.chuseok22.erroralert.core.aggregation.ErrorAggregationContext;
import io.github.chuseok22.erroralert.core.aggregation.ErrorContextHolder;
import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.ErrorLevel;
import io.github.chuseok22.erroralert.core.model.EventType;
import io.github.chuseok22.erroralert.core.util.ThrowableUtils;
import io.github.chuseok22.erroralert.core.worker.AlertGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;

/**
 * Spring MVC HandlerInterceptor로 컨트롤러 예외를 관찰한다.
 * 예외를 삼키지 않고 ErrorAggregationContext에 누적만 한다.
 * ControllerAdvice가 예외를 처리하면 afterCompletion의 ex는 null이 되므로,
 * Logback Appender가 ControllerAdvice 내 log.error를 캡처하는 것이 주 경로다.
 */
public class RequestExceptionObserver implements HandlerInterceptor {

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        if (ex == null || AlertGuard.isProcessing()) return;

        ErrorAggregationContext ctx = ErrorContextHolder.get();
        if (ctx == null) return;

        ErrorEvent event = ErrorEvent.builder()
                .eventType(EventType.EXCEPTION)
                .level(ErrorLevel.ERROR)
                .occurredAt(Instant.now())
                .loggerName(extractHandlerName(handler))
                .threadName(Thread.currentThread().getName())
                .message(ex.getMessage())
                .throwableExists(true)
                .exceptionClass(ex.getClass().getName())
                .exceptionMessage(ex.getMessage())
                .stackTrace(ThrowableUtils.formatStackTrace(ex))
                .executionContextId(ctx.getContextId())
                .build();

        ctx.addError(event);
    }

    private String extractHandlerName(Object handler) {
        if (handler instanceof HandlerMethod hm) {
            return hm.getBeanType().getName() + "#" + hm.getMethod().getName();
        }
        return handler != null ? handler.getClass().getName() : "unknown";
    }
}
