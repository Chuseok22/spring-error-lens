package io.github.chuseok22.erroralert.core.worker;

import io.github.chuseok22.erroralert.core.llm.LlmAnalysisCache;
import io.github.chuseok22.erroralert.core.llm.LlmCacheKey;
import io.github.chuseok22.erroralert.core.llm.LlmClient;
import io.github.chuseok22.erroralert.core.model.LlmAnalysisResult;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;
import io.github.chuseok22.erroralert.core.notification.NotificationDispatcher;
import io.github.chuseok22.erroralert.core.queue.ErrorAlertQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 큐에서 MergedErrorEvent를 꺼내 LLM 분석 후 알림을 전송하는 단일 백그라운드 워커.
 * start()/stop()은 Spring SmartLifecycle Bean에서 호출한다.
 */
public class ErrorAlertWorker {

    private static final Logger log = LoggerFactory.getLogger(ErrorAlertWorker.class);
    private static final long POLL_TIMEOUT_MS = 1_000;
    private static final String THREAD_NAME = "error-alert-worker";

    private final ErrorAlertQueue queue;
    private final LlmClient llmClient;
    private final LlmAnalysisCache analysisCache;
    private final NotificationDispatcher dispatcher;

    private volatile boolean running = false;
    private Thread thread;

    public ErrorAlertWorker(
            ErrorAlertQueue queue,
            LlmClient llmClient,
            LlmAnalysisCache analysisCache,
            NotificationDispatcher dispatcher
    ) {
        this.queue = queue;
        this.llmClient = llmClient;
        this.analysisCache = analysisCache;
        this.dispatcher = dispatcher;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::run, THREAD_NAME);
        thread.setDaemon(true);
        thread.start();
        log.info("ErrorAlertWorker 시작");
    }

    public synchronized void stop(long timeoutMs) {
        if (!running) return;
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        drainRemaining();
        log.info("ErrorAlertWorker 종료");
    }

    private void run() {
        while (running) {
            try {
                MergedErrorEvent event = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (event != null) {
                    process(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void process(MergedErrorEvent event) {
        AlertGuard.enter();
        try {
            LlmAnalysisResult analysis = resolveAnalysis(event);
            MergedErrorEvent enriched = event.withLlmAnalysis(analysis);
            dispatcher.dispatch(enriched);
        } catch (Exception e) {
            log.warn("알림 처리 중 오류 발생 (mergedEventId={})", event.getMergedEventId(), e);
        } finally {
            AlertGuard.exit();
        }
    }

    private LlmAnalysisResult resolveAnalysis(MergedErrorEvent event) {
        LlmCacheKey key = LlmCacheKey.from(event);
        LlmAnalysisResult cached = analysisCache.get(key);
        if (cached != null) {
            return cached;
        }
        LlmAnalysisResult result = llmClient.analyze(event);
        if (result.success()) {
            analysisCache.put(key, result);
        }
        return result;
    }

    private void drainRemaining() {
        List<MergedErrorEvent> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        for (MergedErrorEvent event : remaining) {
            process(event);
        }
    }
}
