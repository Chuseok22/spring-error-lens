package io.github.chuseok22.erroralert.core.llm;

import io.github.chuseok22.erroralert.core.model.LlmAnalysisResult;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;

public interface LlmClient {

    LlmAnalysisResult analyze(MergedErrorEvent event);
}
