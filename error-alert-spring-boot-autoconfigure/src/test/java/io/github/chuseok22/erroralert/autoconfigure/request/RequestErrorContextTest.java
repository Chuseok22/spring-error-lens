package io.github.chuseok22.erroralert.autoconfigure.request;

import io.github.chuseok22.erroralert.core.model.AggregationType;
import io.github.chuseok22.erroralert.core.model.ApplicationInfo;
import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.ErrorLevel;
import io.github.chuseok22.erroralert.core.model.EventType;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;
import io.github.chuseok22.erroralert.core.model.RequestContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestErrorContextTest {

    private static final ApplicationInfo APP_INFO = ApplicationInfo.of("test-app", "1.0.0", "prod");
    private static final RequestContext REQUEST_CTX = RequestContext.of("GET", "/api/test", Collections.emptyMap(), null, null, null);

    @Test
    void 초기_상태에서_hasErrors는_false() {
        RequestErrorContext ctx = new RequestErrorContext(REQUEST_CTX);
        assertThat(ctx.hasErrors()).isFalse();
        assertThat(ctx.getAggregationType()).isEqualTo(AggregationType.REQUEST);
    }

    @Test
    void 에러_추가_후_hasErrors는_true() {
        RequestErrorContext ctx = new RequestErrorContext(REQUEST_CTX);
        ctx.addError(errorEvent());
        assertThat(ctx.hasErrors()).isTrue();
    }

    @Test
    void 단일_에러_merge시_primary는_해당_에러() {
        RequestErrorContext ctx = new RequestErrorContext(REQUEST_CTX);
        ErrorEvent event = errorEvent();
        ctx.addError(event);

        MergedErrorEvent merged = ctx.merge(APP_INFO);

        assertThat(merged.getPrimaryError()).isEqualTo(event);
        assertThat(merged.getRelatedErrors()).isEmpty();
        assertThat(merged.getTotalErrorCount()).isEqualTo(1);
        assertThat(merged.getAggregationType()).isEqualTo(AggregationType.REQUEST);
    }

    @Test
    void 복수_에러_merge시_related에_나머지_포함() {
        RequestErrorContext ctx = new RequestErrorContext(REQUEST_CTX);
        ErrorEvent primary = throwableErrorEvent();
        ErrorEvent related1 = errorEvent();
        ErrorEvent related2 = errorEvent();
        ctx.addError(related1);
        ctx.addError(primary);
        ctx.addError(related2);

        MergedErrorEvent merged = ctx.merge(APP_INFO);

        assertThat(merged.getPrimaryError()).isEqualTo(primary);
        assertThat(merged.getRelatedErrors()).containsExactlyInAnyOrder(related1, related2);
        assertThat(merged.getTotalErrorCount()).isEqualTo(3);
    }

    @Test
    void merge_결과에_requestContext와_applicationInfo_포함() {
        RequestErrorContext ctx = new RequestErrorContext(REQUEST_CTX);
        ctx.addError(errorEvent());

        MergedErrorEvent merged = ctx.merge(APP_INFO);

        assertThat(merged.getRequestContext()).isEqualTo(REQUEST_CTX);
        assertThat(merged.getApplicationInfo()).isEqualTo(APP_INFO);
    }

    private ErrorEvent errorEvent() {
        return ErrorEvent.builder()
                .eventType(EventType.LOG_ERROR)
                .level(ErrorLevel.ERROR)
                .message("test error")
                .build();
    }

    private ErrorEvent throwableErrorEvent() {
        return ErrorEvent.builder()
                .eventType(EventType.LOG_ERROR)
                .level(ErrorLevel.ERROR)
                .message("throwable error")
                .throwableExists(true)
                .exceptionClass("java.lang.NullPointerException")
                .build();
    }
}
