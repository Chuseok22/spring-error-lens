package io.github.chuseok22.erroralert.core.llm;

import io.github.chuseok22.erroralert.core.aggregation.MessageNormalizer;
import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;

import java.util.Objects;

/**
 * LLM 분석 cache key. 요청마다 달라지는 값은 제외하여 cache hit율을 높인다.
 */
public record LlmCacheKey(
        String applicationName,
        String applicationVersion,
        String environment,
        String aggregationType,
        String primaryExceptionClass,
        String primaryNormalizedMessage,
        String primaryTopStackTraceLine
) {
    public static LlmCacheKey from(MergedErrorEvent event) {
        ErrorEvent primary = event.getPrimaryError();
        return new LlmCacheKey(
                event.getApplicationInfo().name(),
                event.getApplicationInfo().version(),
                event.getApplicationInfo().environment(),
                event.getAggregationType().name(),
                primary.getExceptionClass(),
                MessageNormalizer.normalize(primary.getMessage()),
                extractTopStackLine(primary.getStackTrace())
        );
    }

    private static String extractTopStackLine(String stackTrace) {
        if (stackTrace == null) return null;
        for (String line : stackTrace.split("\\n")) {
            if (line.strip().startsWith("at ")) {
                return line.strip();
            }
        }
        return null;
    }
}
