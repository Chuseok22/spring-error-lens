package io.github.chuseok22.erroralert.core.aggregation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageNormalizerTest {

    @Test
    void null_입력이면_null_반환() {
        assertThat(MessageNormalizer.normalize(null)).isNull();
    }

    @Test
    void UUID_치환() {
        String result = MessageNormalizer.normalize("entity 550e8400-e29b-41d4-a716-446655440000 not found");
        assertThat(result).contains("{UUID}").doesNotContain("550e8400");
    }

    @Test
    void 긴_16진수_해시_치환() {
        String result = MessageNormalizer.normalize("hash: abcdef1234567890");
        assertThat(result).contains("{HASH}");
    }

    @Test
    void IP_주소_치환() {
        String result = MessageNormalizer.normalize("connection from 192.168.0.1 refused");
        assertThat(result).contains("{IP}").doesNotContain("192.168.0.1");
    }

    @Test
    void 이메일_치환() {
        String result = MessageNormalizer.normalize("user user@example.com not found");
        assertThat(result).contains("{EMAIL}").doesNotContain("user@example.com");
    }

    @Test
    void 날짜_치환() {
        String result = MessageNormalizer.normalize("created at 2024-01-15 10:30:00");
        assertThat(result).contains("{DATETIME}");
    }

    @Test
    void 긴_숫자_ID_치환() {
        String result = MessageNormalizer.normalize("orderId 12345 not found");
        assertThat(result).contains("{ID}").doesNotContain("12345");
    }

    @Test
    void UUID_먼저_치환된_후_HASH가_적용되지_않음() {
        // UUID는 HASH보다 먼저 치환되므로 UUID 내부의 hex가 HASH로 치환되지 않아야 함
        String input = "id: 550e8400-e29b-41d4-a716-446655440000";
        String result = MessageNormalizer.normalize(input);
        assertThat(result).contains("{UUID}").doesNotContain("{HASH}");
    }

    @Test
    void 최대길이_초과시_잘림() {
        // 'z'는 hex 범위[0-9a-f] 밖이므로 어떤 패턴도 매칭하지 않음
        String longMessage = "z".repeat(300);
        String result = MessageNormalizer.normalize(longMessage);
        assertThat(result).hasSize(200);
    }
}
