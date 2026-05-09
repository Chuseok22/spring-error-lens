package io.github.chuseok22.erroralert.core.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class MergedErrorEvent {

    private final String mergedEventId;
    private final AggregationType aggregationType;
    private final ErrorEvent primaryError;
    private final List<ErrorEvent> relatedErrors;
    private final int totalErrorCount;
    private final Instant firstOccurredAt;
    private final Instant lastOccurredAt;
    private final RequestContext requestContext;
    private final ApplicationInfo applicationInfo;
    private final LlmAnalysisResult llmAnalysis;

    private MergedErrorEvent(Builder builder) {
        this.mergedEventId = builder.mergedEventId;
        this.aggregationType = builder.aggregationType;
        this.primaryError = builder.primaryError;
        this.relatedErrors = builder.relatedErrors != null
                ? Collections.unmodifiableList(builder.relatedErrors)
                : Collections.emptyList();
        this.totalErrorCount = builder.totalErrorCount;
        this.firstOccurredAt = builder.firstOccurredAt;
        this.lastOccurredAt = builder.lastOccurredAt;
        this.requestContext = builder.requestContext;
        this.applicationInfo = builder.applicationInfo;
        this.llmAnalysis = builder.llmAnalysis;
    }

    private MergedErrorEvent(MergedErrorEvent source, LlmAnalysisResult llmAnalysis) {
        this.mergedEventId = source.mergedEventId;
        this.aggregationType = source.aggregationType;
        this.primaryError = source.primaryError;
        this.relatedErrors = source.relatedErrors;
        this.totalErrorCount = source.totalErrorCount;
        this.firstOccurredAt = source.firstOccurredAt;
        this.lastOccurredAt = source.lastOccurredAt;
        this.requestContext = source.requestContext;
        this.applicationInfo = source.applicationInfo;
        this.llmAnalysis = llmAnalysis;
    }

    public MergedErrorEvent withLlmAnalysis(LlmAnalysisResult llmAnalysis) {
        return new MergedErrorEvent(this, llmAnalysis);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getMergedEventId() { return mergedEventId; }
    public AggregationType getAggregationType() { return aggregationType; }
    public ErrorEvent getPrimaryError() { return primaryError; }
    public List<ErrorEvent> getRelatedErrors() { return relatedErrors; }
    public int getTotalErrorCount() { return totalErrorCount; }
    public Instant getFirstOccurredAt() { return firstOccurredAt; }
    public Instant getLastOccurredAt() { return lastOccurredAt; }
    public RequestContext getRequestContext() { return requestContext; }
    public ApplicationInfo getApplicationInfo() { return applicationInfo; }
    public LlmAnalysisResult getLlmAnalysis() { return llmAnalysis; }

    public static final class Builder {
        private String mergedEventId = UUID.randomUUID().toString();
        private AggregationType aggregationType;
        private ErrorEvent primaryError;
        private List<ErrorEvent> relatedErrors;
        private int totalErrorCount;
        private Instant firstOccurredAt;
        private Instant lastOccurredAt;
        private RequestContext requestContext;
        private ApplicationInfo applicationInfo;
        private LlmAnalysisResult llmAnalysis;

        public Builder mergedEventId(String mergedEventId) { this.mergedEventId = mergedEventId; return this; }
        public Builder aggregationType(AggregationType aggregationType) { this.aggregationType = aggregationType; return this; }
        public Builder primaryError(ErrorEvent primaryError) { this.primaryError = primaryError; return this; }
        public Builder relatedErrors(List<ErrorEvent> relatedErrors) { this.relatedErrors = relatedErrors; return this; }
        public Builder totalErrorCount(int totalErrorCount) { this.totalErrorCount = totalErrorCount; return this; }
        public Builder firstOccurredAt(Instant firstOccurredAt) { this.firstOccurredAt = firstOccurredAt; return this; }
        public Builder lastOccurredAt(Instant lastOccurredAt) { this.lastOccurredAt = lastOccurredAt; return this; }
        public Builder requestContext(RequestContext requestContext) { this.requestContext = requestContext; return this; }
        public Builder applicationInfo(ApplicationInfo applicationInfo) { this.applicationInfo = applicationInfo; return this; }
        public Builder llmAnalysis(LlmAnalysisResult llmAnalysis) { this.llmAnalysis = llmAnalysis; return this; }

        public MergedErrorEvent build() {
            if (aggregationType == null) throw new IllegalStateException("aggregationType 필수");
            if (primaryError == null) throw new IllegalStateException("primaryError 필수");
            if (applicationInfo == null) throw new IllegalStateException("applicationInfo 필수");
            return new MergedErrorEvent(this);
        }
    }
}
