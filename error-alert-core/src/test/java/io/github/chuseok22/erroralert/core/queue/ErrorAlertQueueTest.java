package io.github.chuseok22.erroralert.core.queue;

import io.github.chuseok22.erroralert.core.model.AggregationType;
import io.github.chuseok22.erroralert.core.model.ApplicationInfo;
import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.ErrorLevel;
import io.github.chuseok22.erroralert.core.model.EventType;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorAlertQueueTest {

    @Test
    void 이벤트_offer_후_poll로_꺼낼_수_있다() throws InterruptedException {
        ErrorAlertQueue queue = new ErrorAlertQueue(10);
        MergedErrorEvent event = mergedEvent();

        queue.offer(event);
        MergedErrorEvent polled = queue.poll(100, TimeUnit.MILLISECONDS);

        assertThat(polled).isEqualTo(event);
    }

    @Test
    void 큐가_가득_차면_새_이벤트_drop() {
        ErrorAlertQueue queue = new ErrorAlertQueue(2);

        boolean first = queue.offer(mergedEvent());
        boolean second = queue.offer(mergedEvent());
        boolean third = queue.offer(mergedEvent());

        assertThat(first).isTrue();
        assertThat(second).isTrue();
        assertThat(third).isFalse();
        assertThat(queue.size()).isEqualTo(2);
    }

    @Test
    void drainTo로_모든_이벤트를_꺼낼_수_있다() {
        ErrorAlertQueue queue = new ErrorAlertQueue(10);
        queue.offer(mergedEvent());
        queue.offer(mergedEvent());
        queue.offer(mergedEvent());

        List<MergedErrorEvent> drained = new ArrayList<>();
        queue.drainTo(drained);

        assertThat(drained).hasSize(3);
        assertThat(queue.size()).isEqualTo(0);
    }

    @Test
    void 빈_큐에서_poll하면_null_반환() throws InterruptedException {
        ErrorAlertQueue queue = new ErrorAlertQueue(10);

        MergedErrorEvent result = queue.poll(10, TimeUnit.MILLISECONDS);

        assertThat(result).isNull();
    }

    private MergedErrorEvent mergedEvent() {
        ErrorEvent primary = ErrorEvent.builder()
                .eventType(EventType.LOG_ERROR)
                .level(ErrorLevel.ERROR)
                .message("test")
                .build();
        return MergedErrorEvent.builder()
                .aggregationType(AggregationType.REQUEST)
                .primaryError(primary)
                .totalErrorCount(1)
                .applicationInfo(ApplicationInfo.of("test-app", "1.0.0", "prod"))
                .build();
    }
}
