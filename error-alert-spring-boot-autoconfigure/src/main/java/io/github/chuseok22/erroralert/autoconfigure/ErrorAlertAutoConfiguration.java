package io.github.chuseok22.erroralert.autoconfigure;

import io.github.chuseok22.erroralert.autoconfigure.async.ErrorAlertAsyncUncaughtExceptionHandler;
import io.github.chuseok22.erroralert.autoconfigure.llm.InternalLlmClient;
import io.github.chuseok22.erroralert.autoconfigure.notification.DiscordNotificationChannel;
import io.github.chuseok22.erroralert.autoconfigure.notification.TelegramNotificationChannel;
import io.github.chuseok22.erroralert.autoconfigure.request.RequestErrorContextFilter;
import io.github.chuseok22.erroralert.autoconfigure.request.RequestExceptionObserver;
import io.github.chuseok22.erroralert.autoconfigure.scheduler.SchedulerAggregationAspect;
import io.github.chuseok22.erroralert.autoconfigure.version.CompositeVersionResolver;
import io.github.chuseok22.erroralert.core.llm.LlmAnalysisCache;
import io.github.chuseok22.erroralert.core.llm.LlmClient;
import io.github.chuseok22.erroralert.core.model.ApplicationInfo;
import io.github.chuseok22.erroralert.core.notification.NotificationChannel;
import io.github.chuseok22.erroralert.core.notification.NotificationDispatcher;
import io.github.chuseok22.erroralert.core.queue.ErrorAlertQueue;
import io.github.chuseok22.erroralert.core.version.ApplicationVersionResolver;
import io.github.chuseok22.erroralert.core.worker.ErrorAlertWorker;
import io.github.chuseok22.erroralert.logback.ErrorAlertLogbackAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@ConditionalOnProperty(prefix = "error-alert", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ErrorAlertProperties.class)
public class ErrorAlertAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ErrorAlertAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ApplicationVersionResolver applicationVersionResolver(
            ErrorAlertProperties props,
            ObjectProvider<BuildProperties> buildPropertiesProvider
    ) {
        return new CompositeVersionResolver(
                buildPropertiesProvider.getIfAvailable(),
                props.getApplication().getVersion()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ApplicationInfo applicationInfo(
            ErrorAlertProperties props,
            ApplicationVersionResolver versionResolver
    ) {
        return ApplicationInfo.of(
                props.getApplication().getName(),
                versionResolver.resolve(),
                props.getEnvironment()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ErrorAlertQueue errorAlertQueue(ErrorAlertProperties props) {
        return new ErrorAlertQueue(props.getQueue().getCapacity());
    }

    @Bean
    @ConditionalOnMissingBean
    public LlmAnalysisCache llmAnalysisCache(ErrorAlertProperties props) {
        return new LlmAnalysisCache(props.getLlm().getCache().getTtl());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "error-alert.llm", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LlmClient llmClient(ErrorAlertProperties props) {
        ErrorAlertProperties.Llm llm = props.getLlm();
        if (llm.getEndpoint() == null || llm.getEndpoint().isBlank()) {
            log.warn("error-alert.llm.endpoint 미설정 — LLM 분석 비활성화");
            return event -> io.github.chuseok22.erroralert.core.model.LlmAnalysisResult.failure();
        }
        return new InternalLlmClient(llm.getEndpoint(), llm.getApiKey(), llm.getModel(), llm.getTimeout());
    }

    @Bean
    @ConditionalOnMissingBean
    public NotificationDispatcher notificationDispatcher(ErrorAlertProperties props) {
        List<NotificationChannel> channels = new ArrayList<>();
        if (props.getDiscord().isEnabled()) {
            channels.add(new DiscordNotificationChannel(props.getDiscord().getWebhookUrl()));
        }
        if (props.getTelegram().isEnabled()) {
            channels.add(new TelegramNotificationChannel(
                    props.getTelegram().getBotToken(), props.getTelegram().getChatId()));
        }
        return new NotificationDispatcher(channels);
    }

    // Discord/Telegram 둘 다 disabled인 경우 Worker를 등록하지 않음 (계획 26.4)
    @Bean(destroyMethod = "")
    @ConditionalOnProperty(prefix = "error-alert", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SmartLifecycle errorAlertWorkerLifecycle(
            ErrorAlertProperties props,
            ErrorAlertQueue queue,
            LlmClient llmClient,
            LlmAnalysisCache analysisCache,
            NotificationDispatcher dispatcher,
            ApplicationInfo applicationInfo
    ) {
        boolean anyEnabled = props.getDiscord().isEnabled() || props.getTelegram().isEnabled();
        if (!anyEnabled) {
            log.info("Discord/Telegram 모두 비활성화 — ErrorAlertWorker 미시작");
            return noopLifecycle();
        }

        ErrorAlertLogbackAppender appender = new ErrorAlertLogbackAppender();
        appender.setQueue(queue);
        appender.setApplicationInfo(applicationInfo);
        appender.setWarnEnabled(props.getLogging().isWarnEnabled());

        ErrorAlertWorker worker = new ErrorAlertWorker(queue, llmClient, analysisCache, dispatcher);

        return new SmartLifecycle() {
            private boolean running = false;

            @Override
            public void start() {
                // Logback root logger에 Appender 등록
                ch.qos.logback.classic.Logger rootLogger =
                        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
                appender.setContext((ch.qos.logback.classic.LoggerContext) rootLogger.getLoggerContext());
                appender.start();
                rootLogger.addAppender(appender);

                worker.start();
                running = true;
                log.info("ErrorAlertWorker 시작 완료");
            }

            @Override
            public void stop() {
                worker.stop(5_000);
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return Integer.MIN_VALUE;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestErrorContextFilter requestErrorContextFilter(
            ErrorAlertQueue queue,
            ApplicationInfo applicationInfo
    ) {
        return new RequestErrorContextFilter(queue, applicationInfo);
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestExceptionObserver requestExceptionObserver() {
        return new RequestExceptionObserver();
    }

    @Bean
    public WebMvcConfigurer errorAlertMvcConfigurer(RequestExceptionObserver observer) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(observer);
            }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "error-alert.aggregation", name = "scheduler-enabled",
            havingValue = "true", matchIfMissing = true)
    public SchedulerAggregationAspect schedulerAggregationAspect(
            ErrorAlertQueue queue,
            ApplicationInfo applicationInfo
    ) {
        return new SchedulerAggregationAspect(queue, applicationInfo);
    }

    @Bean
    @ConditionalOnProperty(prefix = "error-alert.aggregation", name = "async-enabled",
            havingValue = "true", matchIfMissing = true)
    public AsyncUncaughtExceptionHandler asyncUncaughtExceptionHandler(
            ErrorAlertQueue queue,
            ApplicationInfo applicationInfo
    ) {
        return new ErrorAlertAsyncUncaughtExceptionHandler(queue, applicationInfo);
    }

    private SmartLifecycle noopLifecycle() {
        return new SmartLifecycle() {
            @Override public void start() {}
            @Override public void stop() {}
            @Override public boolean isRunning() { return false; }
            @Override public int getPhase() { return Integer.MIN_VALUE; }
        };
    }
}
