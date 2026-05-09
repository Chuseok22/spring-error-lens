package io.github.chuseok22.erroralert.core.llm;

import io.github.chuseok22.erroralert.core.model.AggregationType;
import io.github.chuseok22.erroralert.core.model.LlmAnalysisResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LlmAnalysisCacheTest {

    @Test
    void put_후_get으로_조회된다() {
        LlmAnalysisCache cache = new LlmAnalysisCache(Duration.ofMinutes(30));
        LlmCacheKey key = key("MyApp", "1.0.0", "prod", "NullPointerException", "null pointer");
        LlmAnalysisResult result = LlmAnalysisResult.success("NPE 분석 결과");

        cache.put(key, result);

        assertThat(cache.get(key)).isEqualTo(result);
    }

    @Test
    void TTL_만료_후_null_반환() throws InterruptedException {
        LlmAnalysisCache cache = new LlmAnalysisCache(Duration.ofMillis(50));
        LlmCacheKey key = key("MyApp", "1.0.0", "prod", "NullPointerException", "null pointer");
        cache.put(key, LlmAnalysisResult.success("분석"));

        Thread.sleep(100);

        assertThat(cache.get(key)).isNull();
    }

    @Test
    void 존재하지_않는_키는_null_반환() {
        LlmAnalysisCache cache = new LlmAnalysisCache(Duration.ofMinutes(30));
        LlmCacheKey key = key("MyApp", "1.0.0", "prod", "SomeException", "some error");

        assertThat(cache.get(key)).isNull();
    }

    @Test
    void 다른_키는_서로_독립적() {
        LlmAnalysisCache cache = new LlmAnalysisCache(Duration.ofMinutes(30));
        LlmCacheKey key1 = key("AppA", "1.0.0", "prod", "NullPointerException", "null");
        LlmCacheKey key2 = key("AppB", "1.0.0", "prod", "NullPointerException", "null");
        LlmAnalysisResult result1 = LlmAnalysisResult.success("분석1");
        LlmAnalysisResult result2 = LlmAnalysisResult.success("분석2");

        cache.put(key1, result1);
        cache.put(key2, result2);

        assertThat(cache.get(key1)).isEqualTo(result1);
        assertThat(cache.get(key2)).isEqualTo(result2);
    }

    private LlmCacheKey key(String app, String version, String env, String exClass, String msg) {
        return new LlmCacheKey(app, version, env, AggregationType.REQUEST.name(), exClass, msg, null);
    }
}
