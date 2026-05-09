package io.github.chuseok22.erroralert.autoconfigure.scheduler;

import io.github.chuseok22.erroralert.core.model.AggregationType;
import io.github.chuseok22.erroralert.core.model.ApplicationInfo;
import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.ErrorLevel;
import io.github.chuseok22.erroralert.core.model.EventType;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerErrorContextTest {

    private static final ApplicationInfo APP_INFO = ApplicationInfo.of("app", "1.0.0", "prod");

    @Test
    void 초기_상태에서_hasErrors는_false() {
        SchedulerErrorContext ctx = new SchedulerErrorContext("MyTask.run");
        assertThat(ctx.hasErrors()).isFalse();
        assertThat(ctx.getAggregationType()).isEqualTo(AggregationType.SCHEDULER);
    }

    @Test
    void 에러_추가_후_merge시_SCHEDULER_타입_MergedErrorEvent_생성() {
        SchedulerErrorContext ctx = new SchedulerErrorContext("MyTask.run");
        ctx.addError(schedulerEvent());

        MergedErrorEvent merged = ctx.merge(APP_INFO);

        assertThat(merged.getAggregationType()).isEqualTo(AggregationType.SCHEDULER);
        assertThat(merged.getPrimaryError().getEventType()).isEqualTo(EventType.SCHEDULER_EXCEPTION);
        assertThat(merged.getTotalErrorCount()).isEqualTo(1);
        assertThat(merged.getApplicationInfo()).isEqualTo(APP_INFO);
    }

    @Test
    void schedulerName을_반환한다() {
        SchedulerErrorContext ctx = new SchedulerErrorContext("MyScheduler.execute");
        assertThat(ctx.getSchedulerName()).isEqualTo("MyScheduler.execute");
    }

    private ErrorEvent schedulerEvent() {
        return ErrorEvent.builder()
                .eventType(EventType.SCHEDULER_EXCEPTION)
                .level(ErrorLevel.ERROR)
                .message("scheduler 에러")
                .throwableExists(true)
                .exceptionClass("java.lang.RuntimeException")
                .build();
    }
}
