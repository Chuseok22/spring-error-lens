package io.github.chuseok22.erroralert.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.github.chuseok22.erroralert.core.aggregation.ErrorContextHolder;
import io.github.chuseok22.erroralert.core.model.AggregationType;
import io.github.chuseok22.erroralert.core.model.ApplicationInfo;
import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.ErrorLevel;
import io.github.chuseok22.erroralert.core.model.EventType;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;
import io.github.chuseok22.erroralert.core.model.RequestContext;
import io.github.chuseok22.erroralert.core.queue.ErrorAlertQueue;
import io.github.chuseok22.erroralert.core.worker.AlertGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorAlertLogbackAppenderTest {

    private LoggerContext ctx;
    private Logger logger;
    private ErrorAlertQueue queue;
    private ErrorAlertLogbackAppender appender;

    @BeforeEach
    void setUp() {
        ctx = new LoggerContext();
        ctx.start();
        logger = ctx.getLogger("TestLogger");
        queue = new ErrorAlertQueue(100);

        appender = new ErrorAlertLogbackAppender();
        appender.setContext(ctx);
        appender.setQueue(queue);
        appender.setApplicationInfo(ApplicationInfo.of("test-app", "1.0.0", "prod"));
        appender.start();
    }

    @AfterEach
    void tearDown() {
        ErrorContextHolder.clear();
        appender.stop();
    }

    @Test
    void 컨텍스트_없으면_STANDALONE으로_즉시_큐_적재() throws InterruptedException {
        appender.append(loggingEvent(Level.ERROR, "에러 발생", null));

        MergedErrorEvent event = queue.poll(100, TimeUnit.MILLISECONDS);

        assertThat(event).isNotNull();
        assertThat(event.getAggregationType()).isEqualTo(AggregationType.STANDALONE);
        assertThat(event.getPrimaryError().getMessage()).isEqualTo("에러 발생");
    }

    @Test
    void 컨텍스트가_있으면_context에_누적하고_큐에_적재하지_않음() throws InterruptedException {
        FakeAggregationContext fakeCtx = new FakeAggregationContext();
        ErrorContextHolder.set(fakeCtx);

        appender.append(loggingEvent(Level.ERROR, "에러", null));

        MergedErrorEvent polled = queue.poll(50, TimeUnit.MILLISECONDS);
        assertThat(polled).isNull();
        assertThat(fakeCtx.getErrors()).hasSize(1);
        assertThat(fakeCtx.getErrors().get(0).getMessage()).isEqualTo("에러");
    }

    @Test
    void AlertGuard_처리_중에는_무시() throws InterruptedException {
        AlertGuard.enter();
        try {
            appender.append(loggingEvent(Level.ERROR, "재귀 에러", null));
        } finally {
            AlertGuard.exit();
        }

        MergedErrorEvent event = queue.poll(50, TimeUnit.MILLISECONDS);
        assertThat(event).isNull();
    }

    @Test
    void warnEnabled_false이면_WARN_무시() throws InterruptedException {
        appender.setWarnEnabled(false);

        appender.append(loggingEvent(Level.WARN, "경고", null));

        assertThat(queue.poll(50, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void warnEnabled_true이면_WARN_처리() throws InterruptedException {
        appender.setWarnEnabled(true);

        appender.append(loggingEvent(Level.WARN, "경고", null));

        MergedErrorEvent event = queue.poll(100, TimeUnit.MILLISECONDS);
        assertThat(event).isNotNull();
        assertThat(event.getPrimaryError().getLevel()).isEqualTo(ErrorLevel.WARN);
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

    private static class FakeAggregationContext implements io.github.chuseok22.erroralert.core.aggregation.ErrorAggregationContext {
        private final List<ErrorEvent> errors = new ArrayList<>();

        @Override public String getContextId() { return "fake-ctx"; }
        @Override public AggregationType getAggregationType() { return AggregationType.REQUEST; }
        @Override public void addError(ErrorEvent event) { errors.add(event); }
        @Override public boolean hasErrors() { return !errors.isEmpty(); }
        @Override public MergedErrorEvent merge(ApplicationInfo applicationInfo) { return null; }
        public List<ErrorEvent> getErrors() { return errors; }
    }
}
