package io.github.chuseok22.erroralert.core.aggregation;

import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.ErrorLevel;
import io.github.chuseok22.erroralert.core.model.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrimaryErrorSelectorTest {

    private PrimaryErrorSelector selector;

    @BeforeEach
    void setUp() {
        selector = new PrimaryErrorSelector();
    }

    @Test
    void 빈_목록이면_null_반환() {
        assertThat(selector.select(List.of())).isNull();
        assertThat(selector.select(null)).isNull();
    }

    @Test
    void Throwable_포함_LOG_ERROR가_최우선() {
        ErrorEvent throwableLogError = event(EventType.LOG_ERROR, ErrorLevel.ERROR, true);
        ErrorEvent throwableException = event(EventType.EXCEPTION, ErrorLevel.ERROR, true);
        ErrorEvent noThrowableError = event(EventType.LOG_ERROR, ErrorLevel.ERROR, false);

        ErrorEvent primary = selector.select(List.of(noThrowableError, throwableException, throwableLogError));

        assertThat(primary).isEqualTo(throwableLogError);
    }

    @Test
    void Throwable_포함_EXCEPTION이_2순위() {
        ErrorEvent throwableException = event(EventType.EXCEPTION, ErrorLevel.ERROR, true);
        ErrorEvent noThrowableError = event(EventType.LOG_ERROR, ErrorLevel.ERROR, false);

        ErrorEvent primary = selector.select(List.of(noThrowableError, throwableException));

        assertThat(primary).isEqualTo(throwableException);
    }

    @Test
    void Throwable_없는_ERROR가_3순위() {
        ErrorEvent noThrowableError = event(EventType.LOG_ERROR, ErrorLevel.ERROR, false);
        ErrorEvent warn = event(EventType.LOG_ERROR, ErrorLevel.WARN, false);

        ErrorEvent primary = selector.select(List.of(warn, noThrowableError));

        assertThat(primary).isEqualTo(noThrowableError);
    }

    @Test
    void WARN만_있으면_WARN이_선정됨() {
        ErrorEvent warn = event(EventType.LOG_ERROR, ErrorLevel.WARN, false);

        ErrorEvent primary = selector.select(List.of(warn));

        assertThat(primary).isEqualTo(warn);
    }

    @Test
    void 동일_우선순위일때_목록_첫번째가_선정됨() {
        ErrorEvent first = event(EventType.LOG_ERROR, ErrorLevel.ERROR, true);
        ErrorEvent second = event(EventType.LOG_ERROR, ErrorLevel.ERROR, true);

        ErrorEvent primary = selector.select(List.of(first, second));

        assertThat(primary).isEqualTo(first);
    }

    private ErrorEvent event(EventType type, ErrorLevel level, boolean throwableExists) {
        return ErrorEvent.builder()
                .eventType(type)
                .level(level)
                .throwableExists(throwableExists)
                .message("test message")
                .build();
    }
}
