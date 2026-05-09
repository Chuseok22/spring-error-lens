package io.github.chuseok22.erroralert.core.model;

public record LlmAnalysisResult(
        boolean success,
        String analysis
) {
    public static LlmAnalysisResult success(String analysis) {
        return new LlmAnalysisResult(true, analysis);
    }

    public static LlmAnalysisResult failure() {
        return new LlmAnalysisResult(false, null);
    }
}
