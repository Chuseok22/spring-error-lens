package io.github.chuseok22.erroralert.core.model;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public final class ErrorEvent {

    private final String eventId;
    private final EventType eventType;
    private final ErrorLevel level;
    private final Instant occurredAt;
    private final String loggerName;
    private final String threadName;
    private final String message;
    private final boolean throwableExists;
    private final String exceptionClass;
    private final String exceptionMessage;
    private final String stackTrace;
    private final Map<String, String> mdc;
    private final RequestContext requestContext;
    private final String executionContextId;

    private ErrorEvent(Builder builder) {
        this.eventId = builder.eventId;
        this.eventType = builder.eventType;
        this.level = builder.level;
        this.occurredAt = builder.occurredAt;
        this.loggerName = builder.loggerName;
        this.threadName = builder.threadName;
        this.message = builder.message;
        this.throwableExists = builder.throwableExists;
        this.exceptionClass = builder.exceptionClass;
        this.exceptionMessage = builder.exceptionMessage;
        this.stackTrace = builder.stackTrace;
        this.mdc = builder.mdc != null ? Collections.unmodifiableMap(builder.mdc) : Collections.emptyMap();
        this.requestContext = builder.requestContext;
        this.executionContextId = builder.executionContextId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getEventId() { return eventId; }
    public EventType getEventType() { return eventType; }
    public ErrorLevel getLevel() { return level; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getLoggerName() { return loggerName; }
    public String getThreadName() { return threadName; }
    public String getMessage() { return message; }
    public boolean isThrowableExists() { return throwableExists; }
    public String getExceptionClass() { return exceptionClass; }
    public String getExceptionMessage() { return exceptionMessage; }
    public String getStackTrace() { return stackTrace; }
    public Map<String, String> getMdc() { return mdc; }
    public RequestContext getRequestContext() { return requestContext; }
    public String getExecutionContextId() { return executionContextId; }

    public static final class Builder {
        private String eventId = UUID.randomUUID().toString();
        private EventType eventType;
        private ErrorLevel level;
        private Instant occurredAt = Instant.now();
        private String loggerName;
        private String threadName;
        private String message;
        private boolean throwableExists;
        private String exceptionClass;
        private String exceptionMessage;
        private String stackTrace;
        private Map<String, String> mdc;
        private RequestContext requestContext;
        private String executionContextId;

        public Builder eventId(String eventId) { this.eventId = eventId; return this; }
        public Builder eventType(EventType eventType) { this.eventType = eventType; return this; }
        public Builder level(ErrorLevel level) { this.level = level; return this; }
        public Builder occurredAt(Instant occurredAt) { this.occurredAt = occurredAt; return this; }
        public Builder loggerName(String loggerName) { this.loggerName = loggerName; return this; }
        public Builder threadName(String threadName) { this.threadName = threadName; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder throwableExists(boolean throwableExists) { this.throwableExists = throwableExists; return this; }
        public Builder exceptionClass(String exceptionClass) { this.exceptionClass = exceptionClass; return this; }
        public Builder exceptionMessage(String exceptionMessage) { this.exceptionMessage = exceptionMessage; return this; }
        public Builder stackTrace(String stackTrace) { this.stackTrace = stackTrace; return this; }
        public Builder mdc(Map<String, String> mdc) { this.mdc = mdc; return this; }
        public Builder requestContext(RequestContext requestContext) { this.requestContext = requestContext; return this; }
        public Builder executionContextId(String executionContextId) { this.executionContextId = executionContextId; return this; }

        public ErrorEvent build() {
            if (eventType == null) throw new IllegalStateException("eventType 필수");
            if (level == null) throw new IllegalStateException("level 필수");
            return new ErrorEvent(this);
        }
    }
}
