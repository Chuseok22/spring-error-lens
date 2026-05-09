package io.github.chuseok22.erroralert.autoconfigure.request;

import io.github.chuseok22.erroralert.core.aggregation.ErrorContextHolder;
import io.github.chuseok22.erroralert.core.model.ApplicationInfo;
import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.ErrorLevel;
import io.github.chuseok22.erroralert.core.model.EventType;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;
import io.github.chuseok22.erroralert.core.model.RequestContext;
import io.github.chuseok22.erroralert.core.queue.ErrorAlertQueue;
import io.github.chuseok22.erroralert.core.util.ThrowableUtils;
import io.github.chuseok22.erroralert.core.worker.AlertGuard;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * request 진입 시 RequestErrorContext를 생성하고, 종료 시 오류가 있으면 MergedErrorEvent를 큐에 적재한다.
 * body 이중 읽기를 위해 멀티파트/바이너리를 제외한 요청은 RepeatableReadRequestWrapper로 감싼다.
 */
public class RequestErrorContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestErrorContextFilter.class);

    private static final List<String> EXCLUDED_CONTENT_TYPE_PREFIXES = List.of(
            "multipart/",
            "application/octet-stream"
    );

    private final ErrorAlertQueue queue;
    private final ApplicationInfo applicationInfo;

    public RequestErrorContextFilter(ErrorAlertQueue queue, ApplicationInfo applicationInfo) {
        this.queue = queue;
        this.applicationInfo = applicationInfo;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        HttpServletRequest effectiveRequest = request;
        if (shouldCacheBody(request)) {
            try {
                effectiveRequest = new RepeatableReadRequestWrapper(request);
            } catch (IOException e) {
                log.warn("request body 캐싱 실패 — 원본 요청으로 진행", e);
            }
        }

        RequestContext requestContext = buildRequestContext(effectiveRequest);
        RequestErrorContext errorContext = new RequestErrorContext(requestContext);
        ErrorContextHolder.set(errorContext);

        try {
            chain.doFilter(effectiveRequest, response);
        } catch (Exception ex) {
            // 필터 체인에서 전파된 예외를 fallback으로 관찰 (이미 Logback Appender에서 캡처된 경우 중복 허용)
            if (!AlertGuard.isProcessing()) {
                errorContext.addError(buildExceptionEvent(ex, errorContext.getContextId()));
            }
            rethrow(ex);
        } finally {
            flushIfNeeded(errorContext);
            ErrorContextHolder.clear();
        }
    }

    private ErrorEvent buildExceptionEvent(Throwable ex, String contextId) {
        return ErrorEvent.builder()
                .eventType(EventType.EXCEPTION)
                .level(ErrorLevel.ERROR)
                .occurredAt(Instant.now())
                .loggerName(RequestErrorContextFilter.class.getName())
                .threadName(Thread.currentThread().getName())
                .message(ex.getMessage())
                .throwableExists(true)
                .exceptionClass(ex.getClass().getName())
                .exceptionMessage(ex.getMessage())
                .stackTrace(ThrowableUtils.formatStackTrace(ex))
                .executionContextId(contextId)
                .build();
    }

    private static void rethrow(Exception ex) throws IOException, ServletException {
        if (ex instanceof RuntimeException re) throw re;
        if (ex instanceof IOException io) throw io;
        if (ex instanceof ServletException se) throw se;
        throw new ServletException(ex);
    }

    private boolean shouldCacheBody(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) return true;
        String lower = contentType.toLowerCase();
        return EXCLUDED_CONTENT_TYPE_PREFIXES.stream().noneMatch(lower::startsWith);
    }

    private RequestContext buildRequestContext(HttpServletRequest request) {
        String body = null;
        if (request instanceof RepeatableReadRequestWrapper wrapper) {
            body = wrapper.getBodyAsString();
        }
        return RequestContext.of(
                request.getMethod(),
                request.getRequestURI(),
                toMultiValueMap(request.getParameterMap()),
                body,
                request.getContentType(),
                request.getRemoteAddr()
        );
    }

    private Map<String, List<String>> toMultiValueMap(Map<String, String[]> parameterMap) {
        if (parameterMap == null || parameterMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> result = new HashMap<>();
        parameterMap.forEach((key, values) -> result.put(key, Arrays.asList(values)));
        return result;
    }

    private void flushIfNeeded(RequestErrorContext errorContext) {
        if (!errorContext.hasErrors()) return;
        try {
            MergedErrorEvent event = errorContext.merge(applicationInfo);
            queue.offer(event);
        } catch (Exception e) {
            log.warn("MergedErrorEvent 생성 또는 큐 적재 실패 (contextId={})", errorContext.getContextId(), e);
        }
    }
}
