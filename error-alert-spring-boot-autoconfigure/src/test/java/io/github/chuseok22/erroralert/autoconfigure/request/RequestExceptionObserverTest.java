package io.github.chuseok22.erroralert.autoconfigure.request;

import io.github.chuseok22.erroralert.core.aggregation.ErrorContextHolder;
import io.github.chuseok22.erroralert.core.model.AggregationType;
import io.github.chuseok22.erroralert.core.model.ApplicationInfo;
import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.ErrorLevel;
import io.github.chuseok22.erroralert.core.model.EventType;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;
import io.github.chuseok22.erroralert.core.model.RequestContext;
import io.github.chuseok22.erroralert.core.worker.AlertGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class RequestExceptionObserverTest {

    private final RequestExceptionObserver observer = new RequestExceptionObserver();

    @AfterEach
    void tearDown() {
        ErrorContextHolder.clear();
        if (AlertGuard.isProcessing()) AlertGuard.exit();
    }

    @Test
    void 예외가_null이면_아무것도_하지_않음() {
        RequestErrorContext ctx = setupContext();

        observer.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), null, null);

        assertThat(ctx.hasErrors()).isFalse();
    }

    @Test
    void 컨텍스트_없으면_아무것도_하지_않음() {
        RuntimeException ex = new RuntimeException("에러");

        observer.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), null, ex);

        // 예외가 발생하지 않으면 통과
    }

    @Test
    void AlertGuard_처리_중에는_무시() {
        RequestErrorContext ctx = setupContext();
        AlertGuard.enter();

        observer.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(),
                null, new RuntimeException("에러"));

        assertThat(ctx.hasErrors()).isFalse();
    }

    @Test
    void 예외_발생_시_컨텍스트에_누적() {
        RequestErrorContext ctx = setupContext();
        RuntimeException ex = new RuntimeException("controller 예외");

        observer.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), null, ex);

        assertThat(ctx.hasErrors()).isTrue();
        MergedErrorEvent merged = ctx.merge(ApplicationInfo.of("app", "1.0", "prod"));
        assertThat(merged.getPrimaryError().getEventType()).isEqualTo(EventType.EXCEPTION);
        assertThat(merged.getPrimaryError().getLevel()).isEqualTo(ErrorLevel.ERROR);
        assertThat(merged.getPrimaryError().getExceptionClass())
                .isEqualTo(RuntimeException.class.getName());
    }

    private RequestErrorContext setupContext() {
        RequestErrorContext ctx = new RequestErrorContext(
                RequestContext.of("GET", "/api/test", Collections.emptyMap(), null, null, null));
        ErrorContextHolder.set(ctx);
        return ctx;
    }
}
