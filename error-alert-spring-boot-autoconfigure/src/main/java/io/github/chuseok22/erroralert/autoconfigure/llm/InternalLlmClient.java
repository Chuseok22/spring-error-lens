package io.github.chuseok22.erroralert.autoconfigure.llm;

import io.github.chuseok22.erroralert.core.llm.LlmClient;
import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.LlmAnalysisResult;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 사내 LLM HTTP API를 호출한다. 타임아웃(기본 10초) 초과 또는 응답 오류 시 LlmAnalysisResult.failure()를 반환한다.
 */
public class InternalLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(InternalLlmClient.class);

    private final HttpClient httpClient;
    private final String endpoint;
    private final String apiKey;
    private final String model;

    public InternalLlmClient(String endpoint, String apiKey, String model, Duration timeout) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    @Override
    public LlmAnalysisResult analyze(MergedErrorEvent event) {
        String prompt = buildPrompt(event);
        String requestBody = buildRequestJson(prompt);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String analysis = extractAnalysis(response.body());
                return LlmAnalysisResult.success(analysis);
            } else {
                log.warn("LLM API 오류 응답 (status={}, mergedEventId={})",
                        response.statusCode(), event.getMergedEventId());
                return LlmAnalysisResult.failure();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("LLM 분석 요청 인터럽트 (mergedEventId={})", event.getMergedEventId());
            return LlmAnalysisResult.failure();
        } catch (Exception e) {
            log.warn("LLM 분석 요청 실패 (mergedEventId={})", event.getMergedEventId(), e);
            return LlmAnalysisResult.failure();
        }
    }

    private String buildPrompt(MergedErrorEvent event) {
        ErrorEvent primary = event.getPrimaryError();
        StringBuilder sb = new StringBuilder();
        sb.append("다음 정보는 하나의 실행 단위(").append(event.getAggregationType()).append(")에서 발생한 에러입니다.\n");
        sb.append("Primary Error를 중심으로 원인을 한국어로 분석하고, Related Errors는 보조 단서로만 참고하세요.\n\n");

        sb.append("=== 애플리케이션 정보 ===\n");
        sb.append("서비스: ").append(event.getApplicationInfo().name()).append("\n");
        sb.append("버전: ").append(event.getApplicationInfo().version()).append("\n");
        sb.append("환경: ").append(event.getApplicationInfo().environment()).append("\n\n");

        if (event.getRequestContext() != null) {
            sb.append("=== 요청 정보 ===\n");
            sb.append("Method: ").append(event.getRequestContext().method()).append("\n");
            sb.append("URI: ").append(event.getRequestContext().uri()).append("\n");
            if (event.getRequestContext().body() != null) {
                sb.append("Body: ").append(event.getRequestContext().body()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("=== Primary Error ===\n");
        sb.append("메시지: ").append(primary.getMessage()).append("\n");
        if (primary.getExceptionClass() != null) {
            sb.append("예외: ").append(primary.getExceptionClass()).append("\n");
        }
        if (primary.getStackTrace() != null) {
            sb.append("스택 트레이스:\n").append(primary.getStackTrace()).append("\n");
        }

        List<ErrorEvent> related = event.getRelatedErrors();
        if (!related.isEmpty()) {
            sb.append("\n=== Related Errors (").append(related.size()).append("건) ===\n");
            for (int i = 0; i < Math.min(related.size(), 3); i++) {
                ErrorEvent r = related.get(i);
                sb.append("[").append(i + 1).append("] ").append(r.getMessage());
                if (r.getExceptionClass() != null) {
                    sb.append(" (").append(r.getExceptionClass()).append(")");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private String buildRequestJson(String prompt) {
        // 사내 LLM API에 맞는 최소 요청 형식. 실제 스키마에 따라 조정 필요.
        String escaped = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "{\"model\":\"" + model + "\",\"messages\":[{\"role\":\"user\",\"content\":\"" + escaped + "\"}]}";
    }

    private String extractAnalysis(String responseBody) {
        // 응답 본문에서 텍스트 추출. 실제 API 응답 구조에 따라 조정 필요.
        // 기본적으로 전체 응답을 분석 결과로 사용하되, "content" 필드가 있으면 추출
        int contentIdx = responseBody.indexOf("\"content\":");
        if (contentIdx >= 0) {
            int start = responseBody.indexOf('"', contentIdx + 10) + 1;
            int end = responseBody.indexOf('"', start);
            if (start > 0 && end > start) {
                return responseBody.substring(start, end)
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"");
            }
        }
        return responseBody;
    }
}
