package io.github.chuseok22.erroralert.core.queue;

import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 크기 제한 큐 (DROP_NEW 정책): 큐가 가득 차면 새 이벤트를 버리고 내부 로그만 남긴다.
 */
public class ErrorAlertQueue {

    private static final Logger log = LoggerFactory.getLogger(ErrorAlertQueue.class);

    private final ArrayBlockingQueue<MergedErrorEvent> queue;

    public ErrorAlertQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    public boolean offer(MergedErrorEvent event) {
        boolean offered = queue.offer(event);
        if (!offered) {
            log.warn("알림 큐 포화로 이벤트 drop (mergedEventId={})", event.getMergedEventId());
        }
        return offered;
    }

    public MergedErrorEvent poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public int drainTo(List<MergedErrorEvent> target) {
        return queue.drainTo(target);
    }

    public int size() {
        return queue.size();
    }
}
