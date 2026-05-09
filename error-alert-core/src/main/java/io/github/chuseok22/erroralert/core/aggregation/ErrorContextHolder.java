package io.github.chuseok22.erroralert.core.aggregation;

/**
 * 현재 실행 단위(request / scheduler / async)의 ErrorAggregationContext를 스레드 로컬로 관리한다.
 * Logback Appender가 로그 이벤트 발생 시 이 홀더를 통해 현재 컨텍스트에 누적한다.
 */
public final class ErrorContextHolder {

    private static final ThreadLocal<ErrorAggregationContext> CONTEXT = new ThreadLocal<>();

    private ErrorContextHolder() {}

    public static ErrorAggregationContext get() {
        return CONTEXT.get();
    }

    public static void set(ErrorAggregationContext context) {
        CONTEXT.set(context);
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
