package io.github.chuseok22.erroralert.autoconfigure.version;

import io.github.chuseok22.erroralert.core.version.ApplicationVersionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.info.BuildProperties;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Manifest;

/**
 * 버전 정보 조회 우선순위:
 * 1) Spring Boot build-info (BuildProperties)
 * 2) Jar Manifest의 Implementation-Version
 * 3) 설정값 (configuredVersion)
 * 4) "unknown"
 */
public class CompositeVersionResolver implements ApplicationVersionResolver {

    private static final Logger log = LoggerFactory.getLogger(CompositeVersionResolver.class);

    private final BuildProperties buildProperties;
    private final String configuredVersion;
    private volatile String cachedVersion;

    public CompositeVersionResolver(BuildProperties buildProperties, String configuredVersion) {
        this.buildProperties = buildProperties;
        this.configuredVersion = configuredVersion;
    }

    @Override
    public String resolve() {
        if (cachedVersion != null) return cachedVersion;
        cachedVersion = doResolve();
        return cachedVersion;
    }

    private String doResolve() {
        // 1순위: Spring Boot build-info
        if (buildProperties != null) {
            String v = buildProperties.getVersion();
            if (isValid(v)) {
                log.debug("버전 조회 성공 (source=build-info): {}", v);
                return v;
            }
        }

        // 2순위: Jar Manifest Implementation-Version
        String manifestVersion = resolveFromManifest();
        if (isValid(manifestVersion)) {
            log.debug("버전 조회 성공 (source=manifest): {}", manifestVersion);
            return manifestVersion;
        }

        // 3순위: 설정값
        if (isValid(configuredVersion)) {
            log.debug("버전 조회 성공 (source=config): {}", configuredVersion);
            return configuredVersion;
        }

        log.debug("버전 정보 없음 — 'unknown' 사용");
        return "unknown";
    }

    private String resolveFromManifest() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("META-INF/MANIFEST.MF")) {
            if (is == null) return null;
            Manifest manifest = new Manifest(is);
            return manifest.getMainAttributes().getValue("Implementation-Version");
        } catch (IOException e) {
            return null;
        }
    }

    private boolean isValid(String value) {
        return value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value);
    }
}
