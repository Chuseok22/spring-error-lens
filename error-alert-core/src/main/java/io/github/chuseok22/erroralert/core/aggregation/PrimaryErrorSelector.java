package io.github.chuseok22.erroralert.core.aggregation;

import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.ErrorLevel;
import io.github.chuseok22.erroralert.core.model.EventType;

import java.util.List;

/**
 * 대표 에러 선정 로직: Throwable 포함 최초 ERROR → Throwable 포함 최초 EXCEPTION 계열 →
 * Throwable 없는 최초 ERROR → 최초 WARN 순서로 선정
 */
public class PrimaryErrorSelector {

    public ErrorEvent select(List<ErrorEvent> events) {
        if (events == null || events.isEmpty()) {
            return null;
        }

        // 1순위: Throwable 포함 + ERROR 레벨 + LOG_ERROR 타입
        for (ErrorEvent event : events) {
            if (event.isThrowableExists() && event.getLevel() == ErrorLevel.ERROR
                    && event.getEventType() == EventType.LOG_ERROR) {
                return event;
            }
        }

        // 2순위: Throwable 포함 + ERROR 레벨 (EXCEPTION, SCHEDULER_EXCEPTION, ASYNC_EXCEPTION)
        for (ErrorEvent event : events) {
            if (event.isThrowableExists() && event.getLevel() == ErrorLevel.ERROR) {
                return event;
            }
        }

        // 3순위: Throwable 없는 최초 ERROR
        for (ErrorEvent event : events) {
            if (event.getLevel() == ErrorLevel.ERROR) {
                return event;
            }
        }

        // 4순위: 최초 WARN
        return events.get(0);
    }
}
