package io.github.chuseok22.erroralert.core.aggregation;

import io.github.chuseok22.erroralert.core.model.AggregationType;
import io.github.chuseok22.erroralert.core.model.ApplicationInfo;
import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;

public interface ErrorAggregationContext {

    String getContextId();

    AggregationType getAggregationType();

    void addError(ErrorEvent event);

    boolean hasErrors();

    MergedErrorEvent merge(ApplicationInfo applicationInfo);
}
