package io.github.chuseok22.erroralert.core.model;

public record ApplicationInfo(
        String name,
        String version,
        String environment
) {
    public static ApplicationInfo of(String name, String version, String environment) {
        return new ApplicationInfo(name, version, environment);
    }
}
