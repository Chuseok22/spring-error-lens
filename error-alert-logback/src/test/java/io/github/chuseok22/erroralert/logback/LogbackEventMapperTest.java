package io.github.chuseok22.erroralert.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.ErrorLevel;
import io.github.chuseok22.erroralert.core.model.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogbackEventMapperTest {

    private LoggerContext ctx;
    private Logger logger;

    @BeforeEach
    void setUp() {
        ctx = new LoggerContext();
        ctx.start();
        logger = ctx.getLogger("TestLogger");
    }

    @Test
    void ERROR_로그_이벤트를_ErrorEvent로_변환() {
        LoggingEvent event = loggingEvent(Level.ERROR, "에러 발생", null);

        ErrorEvent result = LogbackEventMapper.map(event, "ctx-123");

        assertThat(result.getEventType()).isEqualTo(EventType.LOG_ERROR);
        assertThat(result.getLevel()).isEqualTo(ErrorLevel.ERROR);
        assertThat(result.getMessage()).isEqualTo("에러 발생");
        assertThat(result.isThrowableExists()).isFalse();
        assertThat(result.getExecutionContextId()).isEqualTo("ctx-123");
        assertThat(result.getLoggerName()).isEqualTo("TestLogger");
    }

    @Test
    void WARN_로그_이벤트는_WARN_레벨로_변환() {
        LoggingEvent event = loggingEvent(Level.WARN, "경고", null);

        ErrorEvent result = LogbackEventMapper.map(event, null);

        assertThat(result.getLevel()).isEqualTo(ErrorLevel.WARN);
    }

    @Test
    void Throwable_포함_이벤트에서_예외_정보_추출() {
        RuntimeException ex = new RuntimeException("테스트 예외");
        LoggingEvent event = loggingEvent(Level.ERROR, "에러", ex);

        ErrorEvent result = LogbackEventMapper.map(event, null);

        assertThat(result.isThrowableExists()).isTrue();
        assertThat(result.getExceptionClass()).isEqualTo(RuntimeException.class.getName());
        assertThat(result.getExceptionMessage()).isEqualTo("테스트 예외");
        assertThat(result.getStackTrace()).contains("java.lang.RuntimeException");
    }

    @Test
    void executionContextId가_null이면_null_유지() {
        LoggingEvent event = loggingEvent(Level.ERROR, "msg", null);

        ErrorEvent result = LogbackEventMapper.map(event, null);

        assertThat(result.getExecutionContextId()).isNull();
    }

    private LoggingEvent loggingEvent(Level level, String message, Throwable throwable) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerContext(ctx);
        event.setLoggerName(logger.getName());
        event.setLevel(level);
        event.setMessage(message);
        event.setTimeStamp(System.currentTimeMillis());
        if (throwable != null) {
            event.setThrowableProxy(new ch.qos.logback.classic.spi.ThrowableProxy(throwable));
        }
        return event;
    }
}
