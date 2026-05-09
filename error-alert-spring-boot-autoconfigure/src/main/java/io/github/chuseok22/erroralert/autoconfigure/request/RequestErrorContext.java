package io.github.chuseok22.erroralert.autoconfigure.request;

import io.github.chuseok22.erroralert.core.aggregation.ErrorAggregationContext;
import io.github.chuseok22.erroralert.core.aggregation.PrimaryErrorSelector;
import io.github.chuseok22.erroralert.core.model.AggregationType;
import io.github.chuseok22.erroralert.core.model.ApplicationInfo;
import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;
import io.github.chuseok22.erroralert.core.model.RequestContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 하나의 HTTP request 실행 단위에서 발생한 ErrorEvent를 수집한다.
 * request 종료 시 filter가 merge()를 호출해 MergedErrorEvent를 생성한다.
 */
public class RequestErrorContext implements ErrorAggregationContext {

    private static final PrimaryErrorSelector SELECTOR = new PrimaryErrorSelector();

    private final String contextId;
    private final RequestContext requestContext;
    private final List<ErrorEvent> errors = new CopyOnWriteArrayList<>();

    public RequestErrorContext(RequestContext requestContext) {
        this.contextId = UUID.randomUUID().toString();
        this.requestContext = requestContext;
    }

    @Override
    public String getContextId() {
        return contextId;
    }

    @Override
    public AggregationType getAggregationType() {
        return AggregationType.REQUEST;
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

        Instant first = all.stream()
                .map(ErrorEvent::getOccurredAt)
                .min(Comparator.naturalOrder())
                .orElse(Instant.now());

        Instant last = all.stream()
                .map(ErrorEvent::getOccurredAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.now());

        return MergedErrorEvent.builder()
                .aggregationType(AggregationType.REQUEST)
                .primaryError(primary)
                .relatedErrors(related)
                .totalErrorCount(all.size())
                .firstOccurredAt(first)
                .lastOccurredAt(last)
                .requestContext(requestContext)
                .applicationInfo(applicationInfo)
                .build();
    }
}
