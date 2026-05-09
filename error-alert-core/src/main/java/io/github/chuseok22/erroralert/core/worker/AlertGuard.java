package io.github.chuseok22.erroralert.core.worker;

/**
 * 재귀 알림 방지용 ThreadLocal 플래그.
 * 알림 처리 중 발생한 log.error가 다시 알림을 생성하는 무한 루프를 막는다.
 */
public final class AlertGuard {

    private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private AlertGuard() {}

    public static boolean isProcessing() {
        return PROCESSING.get();
    }

    public static void enter() {
        PROCESSING.set(Boolean.TRUE);
    }

    public static void exit() {
        PROCESSING.remove();
    }
}
