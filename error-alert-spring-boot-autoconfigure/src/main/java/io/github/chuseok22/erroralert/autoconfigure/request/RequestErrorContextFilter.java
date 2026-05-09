package io.github.chuseok22.erroralert.autoconfigure.request;

import io.github.chuseok22.erroralert.core.aggregation.ErrorContextHolder;
import io.github.chuseok22.erroralert.core.model.ApplicationInfo;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;
import io.github.chuseok22.erroralert.core.model.RequestContext;
import io.github.chuseok22.erroralert.core.queue.ErrorAlertQueue;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
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
        } finally {
            flushIfNeeded(errorContext);
            ErrorContextHolder.clear();
        }
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
