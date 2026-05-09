package io.github.chuseok22.erroralert.core.llm;

import io.github.chuseok22.erroralert.core.model.LlmAnalysisResult;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TTL 기반 인메모리 LLM 분석 결과 캐시 (TTL 기본값: 30분).
 * 만료된 항목은 조회 시점에 지연 삭제한다.
 */
public class LlmAnalysisCache {

    private final long ttlMs;
    private final ConcurrentHashMap<LlmCacheKey, CacheEntry> store = new ConcurrentHashMap<>();

    public LlmAnalysisCache(Duration ttl) {
        this.ttlMs = ttl.toMillis();
    }

    public LlmAnalysisResult get(LlmCacheKey key) {
        CacheEntry entry = store.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.timestamp() > ttlMs) {
            store.remove(key, entry);
            return null;
        }
        return entry.result();
    }

    public void put(LlmCacheKey key, LlmAnalysisResult result) {
        store.put(key, new CacheEntry(result, System.currentTimeMillis()));
    }

    public int size() {
        return store.size();
    }

    private record CacheEntry(LlmAnalysisResult result, long timestamp) {}
}
