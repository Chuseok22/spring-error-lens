package io.github.chuseok22.erroralert.autoconfigure.notification;

import io.github.chuseok22.erroralert.core.model.ErrorEvent;
import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;
import io.github.chuseok22.erroralert.core.notification.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Telegram Bot API로 알림을 전송한다. 메시지가 Telegram 제한(4096자)을 초과하면 청크로 분할한다.
 */
public class TelegramNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationChannel.class);
    private static final int MAX_MESSAGE_LENGTH = 4000;

    private final String botToken;
    private final String chatId;
    private final HttpClient httpClient;

    public TelegramNotificationChannel(String botToken, String chatId) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getName() {
        return "telegram";
    }

    @Override
    public boolean isEnabled() {
        return botToken != null && !botToken.isBlank()
                && chatId != null && !chatId.isBlank();
    }

    @Override
    public void send(MergedErrorEvent event) {
        String fullMessage = formatMessage(event);
        List<String> chunks = splitIntoChunks(fullMessage, MAX_MESSAGE_LENGTH);

        for (String chunk : chunks) {
            sendChunk(chunk);
        }
    }

    private void sendChunk(String text) {
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String url = "https://api.telegram.org/bot" + botToken
                + "/sendMessage?chat_id=" + chatId + "&text=" + encoded;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Telegram 전송 실패 (status={})", response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Telegram 전송 인터럽트", e);
        } catch (Exception e) {
            log.warn("Telegram 전송 오류", e);
        }
    }

    private String formatMessage(MergedErrorEvent event) {
        ErrorEvent primary = event.getPrimaryError();
        StringBuilder sb = new StringBuilder();
        sb.append("🚨 ").append(event.getApplicationInfo().name()).append(" — 에러 알림\n");
        sb.append("버전: ").append(event.getApplicationInfo().version())
          .append(" | 환경: ").append(event.getApplicationInfo().environment()).append("\n");
        sb.append("집계 타입: ").append(event.getAggregationType()).append("\n\n");

        if (event.getRequestContext() != null) {
            sb.append("요청: ").append(event.getRequestContext().method())
              .append(" ").append(event.getRequestContext().uri()).append("\n");
        }

        sb.append("Primary Error: ").append(primary.getMessage()).append("\n");
        if (primary.getExceptionClass() != null) {
            sb.append("예외: ").append(primary.getExceptionClass()).append("\n");
        }

        if (event.getLlmAnalysis() != null && event.getLlmAnalysis().success()) {
            sb.append("\nLLM 분석:\n").append(event.getLlmAnalysis().analysis()).append("\n");
        }

        if (primary.getStackTrace() != null) {
            sb.append("\n스택 트레이스:\n").append(primary.getStackTrace());
        }

        return sb.toString();
    }

    private List<String> splitIntoChunks(String text, int maxLength) {
        if (text.length() <= maxLength) return List.of(text);

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLength, text.length());
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }
}
