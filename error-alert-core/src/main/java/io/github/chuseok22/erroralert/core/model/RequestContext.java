package io.github.chuseok22.erroralert.core.model;

import java.util.List;
import java.util.Map;

public record RequestContext(
        String method,
        String uri,
        Map<String, List<String>> queryParameters,
        String body,
        String contentType,
        String remoteAddress
) {
    public static RequestContext of(
            String method,
            String uri,
            Map<String, List<String>> queryParameters,
            String body,
            String contentType,
            String remoteAddress
    ) {
        return new RequestContext(method, uri, queryParameters, body, contentType, remoteAddress);
    }
}
