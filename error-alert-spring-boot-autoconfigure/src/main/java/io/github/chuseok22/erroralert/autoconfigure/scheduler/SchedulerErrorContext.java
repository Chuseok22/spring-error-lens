package io.github.chuseok22.erroralert.autoconfigure.scheduler;

import io.github.chuseok22.erroralert.core.aggregation.ErrorAggregationContext;
import io.github.chuseok22.erroralert.core.aggregation.PrimaryErrorSelector;
import io.github.chuseok22.erroralert.core.model.AggregationType;
import io.github.chuseok22.erroralert.core.model.ApplicationInfo;
import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 하나의 scheduler 실행 단위에서 발생한 ErrorEvent를 수집한다.
 */
public class SchedulerErrorContext implements ErrorAggregationContext {

    private static final PrimaryErrorSelector SELECTOR = new PrimaryErrorSelector();

    private final String contextId;
    private final String schedulerName;
    private final Instant startedAt;
    private final List<ErrorEvent> errors = new CopyOnWriteArrayList<>();

    public SchedulerErrorContext(String schedulerName) {
        this.contextId = UUID.randomUUID().toString();
        this.schedulerName = schedulerName;
        this.startedAt = Instant.now();
    }

    @Override
    public String getContextId() {
        return contextId;
    }

    @Override
    public AggregationType getAggregationType() {
        return AggregationType.SCHEDULER;
    }

    @Override
    public void addError(ErrorEvent event) {
        errors.add(event);
    }

    @Override
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    public MergedErrorEvent merge(ApplicationInfo applicationInfo) {
        List<ErrorEvent> all = new ArrayList<>(errors);
        ErrorEvent primary = SELECTOR.select(all);

        List<ErrorEvent> related = all.stream()
                .filter(e -> !e.getEventId().equals(primary.getEventId()))
                .collect(Collectors.toList());

        Instant last = all.stream()
                .map(ErrorEvent::getOccurredAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.now());

        return MergedErrorEvent.builder()
                .aggregationType(AggregationType.SCHEDULER)
                .primaryError(primary)
                .relatedErrors(related)
                .totalErrorCount(all.size())
                .firstOccurredAt(startedAt)
                .lastOccurredAt(last)
                .applicationInfo(applicationInfo)
                .build();
    }

    public String getSchedulerName() {
        return schedulerName;
    }
}
