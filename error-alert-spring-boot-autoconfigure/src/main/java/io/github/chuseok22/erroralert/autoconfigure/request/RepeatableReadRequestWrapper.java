package io.github.chuseok22.erroralert.autoconfigure.request;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * request body를 최대 256KB까지 내부 byte[]에 캐싱하여 반복 읽기를 지원하는 래퍼.
 * Controller까지 InputStream이 정상적으로 전달되도록 getInputStream()/getReader()를 오버라이드한다.
 */
public class RepeatableReadRequestWrapper extends HttpServletRequestWrapper {

    static final int MAX_BODY_BYTES = 256 * 1024;

    private final byte[] body;
    private final boolean truncated;

    public RepeatableReadRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        byte[] buffer = new byte[MAX_BODY_BYTES + 1];
        int bytesRead;
        try (InputStream is = request.getInputStream()) {
            bytesRead = readFully(is, buffer);
        }
        this.truncated = bytesRead > MAX_BODY_BYTES;
        int actualLength = truncated ? MAX_BODY_BYTES : bytesRead;
        this.body = new byte[actualLength];
        System.arraycopy(buffer, 0, this.body, 0, actualLength);
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream bais = new ByteArrayInputStream(body);
        return new CachedServletInputStream(bais);
    }

    @Override
    public BufferedReader getReader() {
        String encoding = getCharacterEncoding();
        Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    public String getBodyAsString() {
        String encoding = getCharacterEncoding();
        Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        String text = new String(body, charset);
        return truncated ? text + " [TRUNCATED]" : text;
    }

    public boolean isTruncated() {
        return truncated;
    }

    private int readFully(InputStream is, byte[] buffer) throws IOException {
        int total = 0;
        int read;
        while (total < buffer.length && (read = is.read(buffer, total, buffer.length - total)) != -1) {
            total += read;
        }
        return total;
    }

    private static class CachedServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream delegate;

        CachedServletInputStream(ByteArrayInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("setReadListener not supported");
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return delegate.read(b, off, len);
        }
    }
}
