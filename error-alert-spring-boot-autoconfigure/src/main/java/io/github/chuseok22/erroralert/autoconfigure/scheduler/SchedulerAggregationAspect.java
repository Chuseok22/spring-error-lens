package io.github.chuseok22.erroralert.autoconfigure.scheduler;

import io.github.chuseok22.erroralert.core.aggregation.ErrorContextHolder;
import io.github.chuseok22.erroralert.core.model.ApplicationInfo;
import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.ErrorLevel;
import io.github.chuseok22.erroralert.core.model.EventType;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;
import io.github.chuseok22.erroralert.core.queue.ErrorAlertQueue;
import io.github.chuseok22.erroralert.core.util.ThrowableUtils;
import io.github.chuseok22.erroralert.core.worker.AlertGuard;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * @Scheduled 메서드 실행 단위를 감싸 SchedulerErrorContext를 생성하고,
 * 실행 중 발생한 에러를 수집한 뒤 종료 시 MergedErrorEvent를 큐에 적재한다.
 */
@Aspect
public class SchedulerAggregationAspect {

    private static final Logger log = LoggerFactory.getLogger(SchedulerAggregationAspect.class);

    private final ErrorAlertQueue queue;
    private final ApplicationInfo applicationInfo;

    public SchedulerAggregationAspect(ErrorAlertQueue queue, ApplicationInfo applicationInfo) {
        this.queue = queue;
        this.applicationInfo = applicationInfo;
    }

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object aroundScheduled(ProceedingJoinPoint pjp) throws Throwable {
        if (AlertGuard.isProcessing()) {
            return pjp.proceed();
        }

        String schedulerName = pjp.getSignature().toShortString();
        SchedulerErrorContext ctx = new SchedulerErrorContext(schedulerName);
        ErrorContextHolder.set(ctx);

        try {
            return pjp.proceed();
        } catch (Throwable ex) {
            ctx.addError(buildExceptionEvent(ex, ctx.getContextId(), schedulerName));
            throw ex;
        } finally {
            if (ctx.hasErrors()) {
                try {
                    MergedErrorEvent event = ctx.merge(applicationInfo);
                    queue.offer(event);
                } catch (Exception e) {
                    log.warn("scheduler MergedErrorEvent 생성 실패 (scheduler={})", schedulerName, e);
                }
            }
            ErrorContextHolder.clear();
        }
    }

    private ErrorEvent buildExceptionEvent(Throwable ex, String contextId, String schedulerName) {
        return ErrorEvent.builder()
                .eventType(EventType.SCHEDULER_EXCEPTION)
                .level(ErrorLevel.ERROR)
                .occurredAt(Instant.now())
                .loggerName(schedulerName)
                .threadName(Thread.currentThread().getName())
                .message(ex.getMessage())
                .throwableExists(true)
                .exceptionClass(ex.getClass().getName())
                .exceptionMessage(ex.getMessage())
                .stackTrace(ThrowableUtils.formatStackTrace(ex))
                .executionContextId(contextId)
                .build();
    }
}
