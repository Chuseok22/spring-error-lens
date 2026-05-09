package io.github.chuseok22.erroralert.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.github.chuseok22.erroralert.core.aggregation.ErrorAggregationContext;
import io.github.chuseok22.erroralert.core.aggregation.ErrorContextHolder;
import io.github.chuseok22.erroralert.core.model.AggregationType;
import io.github.chuseok22.erroralert.core.model.ApplicationInfo;
import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;
import io.github.chuseok22.erroralert.core.queue.ErrorAlertQueue;
import io.github.chuseok22.erroralert.core.worker.AlertGuard;

import java.time.Instant;

/**
 * Logback Appender 구현체. log.error/warn 발생 시 ErrorEvent를 생성하여 처리한다.
 *
 * <ul>
 *   <li>AlertGuard: 알림 처리 중 발생한 로그는 무시하여 재귀 루프를 방지한다.</li>
 *   <li>활성 ErrorAggregationContext가 있으면 컨텍스트에 누적하고 즉시 큐에 적재하지 않는다.</li>
 *   <li>컨텍스트가 없으면 STANDALONE MergedErrorEvent를 생성하여 즉시 큐에 적재한다.</li>
 * </ul>
 */
public class ErrorAlertLogbackAppender extends AppenderBase<ILoggingEvent> {

    private ErrorAlertQueue queue;
    private ApplicationInfo applicationInfo;
    private boolean warnEnabled = false;

    @Override
    protected void append(ILoggingEvent event) {
        if (AlertGuard.isProcessing()) return;
        if (!shouldProcess(event)) return;

        ErrorAggregationContext ctx = ErrorContextHolder.get();
        String contextId = ctx != null ? ctx.getContextId() : null;
        ErrorEvent errorEvent = LogbackEventMapper.map(event, contextId);

        if (ctx != null) {
            ctx.addError(errorEvent);
        } else {
            dispatchStandalone(errorEvent);
        }
    }

    private boolean shouldProcess(ILoggingEvent event) {
        Level level = event.getLevel();
        if (level == Level.ERROR) return true;
        return warnEnabled && level == Level.WARN;
    }

    private void dispatchStandalone(ErrorEvent errorEvent) {
        if (queue == null || applicationInfo == null) return;

        MergedErrorEvent merged = MergedErrorEvent.builder()
                .aggregationType(AggregationType.STANDALONE)
                .primaryError(errorEvent)
                .totalErrorCount(1)
                .firstOccurredAt(errorEvent.getOccurredAt())
                .lastOccurredAt(errorEvent.getOccurredAt())
                .applicationInfo(applicationInfo)
                .build();
        queue.offer(merged);
    }

    public void setQueue(ErrorAlertQueue queue) {
        this.queue = queue;
    }

    public void setApplicationInfo(ApplicationInfo applicationInfo) {
        this.applicationInfo = applicationInfo;
    }

    public void setWarnEnabled(boolean warnEnabled) {
        this.warnEnabled = warnEnabled;
    }
}
