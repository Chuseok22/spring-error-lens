package io.github.chuseok22.erroralert.core.aggregation;

import java.util.regex.Pattern;

/**
 * LLM cache key용 메시지 정규화. 요청마다 달라지는 값(UUID, ID, IP 등)을 플레이스홀더로 치환하여
 * cache hit율을 높인다.
 */
public class MessageNormalizer {

    private static final int MAX_LENGTH = 200;

    // 적용 순서: UUID → HASH → IP → EMAIL → DATETIME → ID (긴 패턴 우선)
    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern HASH_PATTERN =
            Pattern.compile("\\b[0-9a-f]{8,}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern IP_PATTERN =
            Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");
    private static final Pattern DATETIME_PATTERN =
            Pattern.compile("\\d{4}[-/]\\d{2}[-/]\\d{2}([ T]\\d{2}:\\d{2}:\\d{2})?");
    private static final Pattern ID_PATTERN =
            Pattern.compile("\\b\\d{4,}\\b");

    private MessageNormalizer() {}

    public static String normalize(String message) {
        if (message == null) {
            return null;
        }
        String result = message;
        result = UUID_PATTERN.matcher(result).replaceAll("{UUID}");
        result = HASH_PATTERN.matcher(result).replaceAll("{HASH}");
        result = IP_PATTERN.matcher(result).replaceAll("{IP}");
        result = EMAIL_PATTERN.matcher(result).replaceAll("{EMAIL}");
        result = DATETIME_PATTERN.matcher(result).replaceAll("{DATETIME}");
        result = ID_PATTERN.matcher(result).replaceAll("{ID}");

        if (result.length() > MAX_LENGTH) {
            result = result.substring(0, MAX_LENGTH);
        }
        return result;
    }
}
