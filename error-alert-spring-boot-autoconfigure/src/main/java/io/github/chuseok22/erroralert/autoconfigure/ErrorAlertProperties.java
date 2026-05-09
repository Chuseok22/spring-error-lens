package io.github.chuseok22.erroralert.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "error-alert")
public class ErrorAlertProperties {

    private boolean enabled = true;
    private String environment = "prod";

    private final Application application = new Application();
    private final Logging logging = new Logging();
    private final Queue queue = new Queue();
    private final Llm llm = new Llm();
    private final Discord discord = new Discord();
    private final Telegram telegram = new Telegram();

    // ── Getters / Setters ────────────────────────────────────────────────────

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public Application getApplication() { return application; }
    public Logging getLogging() { return logging; }
    public Queue getQueue() { return queue; }
    public Llm getLlm() { return llm; }
    public Discord getDiscord() { return discord; }
    public Telegram getTelegram() { return telegram; }

    // ── Nested classes ───────────────────────────────────────────────────────

    public static class Application {
        private String name = "${spring.application.name:unknown}";
        private String version;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }

    public static class Logging {
        private boolean warnEnabled = false;

        public boolean isWarnEnabled() { return warnEnabled; }
        public void setWarnEnabled(boolean warnEnabled) { this.warnEnabled = warnEnabled; }
    }

    public static class Queue {
        private int capacity = 1000;

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
    }

    public static class Llm {
        private boolean enabled = true;
        private String endpoint;
        private String apiKey;
        private String model;
        private Duration timeout = Duration.ofSeconds(10);
        private final Cache cache = new Cache();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public Cache getCache() { return cache; }

        public static class Cache {
            private boolean enabled = true;
            private Duration ttl = Duration.ofMinutes(30);

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public Duration getTtl() { return ttl; }
            public void setTtl(Duration ttl) { this.ttl = ttl; }
        }
    }

    public static class Discord {
        private boolean enabled = false;
        private String webhookUrl;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getWebhookUrl() { return webhookUrl; }
        public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    }

    public static class Telegram {
        private boolean enabled = false;
        private String botToken;
        private String chatId;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBotToken() { return botToken; }
        public void setBotToken(String botToken) { this.botToken = botToken; }
        public String getChatId() { return chatId; }
        public void setChatId(String chatId) { this.chatId = chatId; }
    }
}
