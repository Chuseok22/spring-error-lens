package io.github.chuseok22.erroralert.autoconfigure.async;

import io.github.chuseok22.erroralert.core.model.AggregationType;
import io.github.chuseok22.erroralert.core.model.ApplicationInfo;
import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.ErrorLevel;
import io.github.chuseok22.erroralert.core.model.EventType;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;
import io.github.chuseok22.erroralert.core.queue.ErrorAlertQueue;
import io.github.chuseok22.erroralert.core.util.ThrowableUtils;
import io.github.chuseok22.erroralert.core.worker.AlertGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;
import java.time.Instant;

/**
 * @Async void 메서드에서 발생한 미처리 예외를 감지하여 알림 큐에 적재한다.
 * CompletableFuture / Future 내부에서 소비된 예외는 자동 감지되지 않는다 (문서화 필요).
 */
public class ErrorAlertAsyncUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorAlertAsyncUncaughtExceptionHandler.class);

    private final ErrorAlertQueue queue;
    private final ApplicationInfo applicationInfo;

    public ErrorAlertAsyncUncaughtExceptionHandler(ErrorAlertQueue queue, ApplicationInfo applicationInfo) {
        this.queue = queue;
        this.applicationInfo = applicationInfo;
    }

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        if (AlertGuard.isProcessing()) return;

        String handlerName = method.getDeclaringClass().getName() + "#" + method.getName();
        Instant now = Instant.now();

        ErrorEvent errorEvent = ErrorEvent.builder()
                .eventType(EventType.ASYNC_EXCEPTION)
                .level(ErrorLevel.ERROR)
                .occurredAt(now)
                .loggerName(handlerName)
                .threadName(Thread.currentThread().getName())
                .message(ex.getMessage())
                .throwableExists(true)
                .exceptionClass(ex.getClass().getName())
                .exceptionMessage(ex.getMessage())
                .stackTrace(ThrowableUtils.formatStackTrace(ex))
                .build();

        MergedErrorEvent merged = MergedErrorEvent.builder()
                .aggregationType(AggregationType.ASYNC)
                .primaryError(errorEvent)
                .totalErrorCount(1)
                .firstOccurredAt(now)
                .lastOccurredAt(now)
                .applicationInfo(applicationInfo)
                .build();

        try {
            queue.offer(merged);
        } catch (Exception e) {
            log.warn("async 예외 이벤트 큐 적재 실패 (handler={})", handlerName, e);
        }
    }
}
