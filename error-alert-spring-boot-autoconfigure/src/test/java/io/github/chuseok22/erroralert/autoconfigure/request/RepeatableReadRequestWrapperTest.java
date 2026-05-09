package io.github.chuseok22.erroralert.autoconfigure.request;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RepeatableReadRequestWrapperTest {

    @Test
    void body를_반복해서_읽을_수_있다() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8));
        request.setContentType("application/json");

        RepeatableReadRequestWrapper wrapper = new RepeatableReadRequestWrapper(request);

        // 첫 번째 읽기
        String first = new String(wrapper.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        // 두 번째 읽기
        String second = new String(wrapper.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(first).isEqualTo("{\"key\":\"value\"}");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void getBodyAsString으로_본문을_문자열로_조회() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("hello body".getBytes(StandardCharsets.UTF_8));
        request.setCharacterEncoding("UTF-8");

        RepeatableReadRequestWrapper wrapper = new RepeatableReadRequestWrapper(request);

        assertThat(wrapper.getBodyAsString()).isEqualTo("hello body");
    }

    @Test
    void 최대_크기_초과시_truncate_후_TRUNCATED_표시() throws IOException {
        byte[] largeBody = new byte[RepeatableReadRequestWrapper.MAX_BODY_BYTES + 100];
        java.util.Arrays.fill(largeBody, (byte) 'x');

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(largeBody);
        request.setCharacterEncoding("UTF-8");

        RepeatableReadRequestWrapper wrapper = new RepeatableReadRequestWrapper(request);

        assertThat(wrapper.isTruncated()).isTrue();
        assertThat(wrapper.getBodyAsString()).endsWith("[TRUNCATED]");
    }

    @Test
    void 빈_본문이면_빈_문자열_반환() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();

        RepeatableReadRequestWrapper wrapper = new RepeatableReadRequestWrapper(request);

        assertThat(wrapper.getBodyAsString()).isEmpty();
        assertThat(wrapper.isTruncated()).isFalse();
    }
}
