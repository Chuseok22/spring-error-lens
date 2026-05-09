package io.github.chuseok22.erroralert.core.notification;

import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 활성화된 알림 채널에 이벤트를 전달한다. 채널 전송 실패는 내부 로그만 남기고 무시한다.
 */
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final List<NotificationChannel> channels;

    public NotificationDispatcher(List<NotificationChannel> channels) {
        this.channels = List.copyOf(channels);
    }

    public void dispatch(MergedErrorEvent event) {
        for (NotificationChannel channel : channels) {
            if (!channel.isEnabled()) continue;
            try {
                channel.send(event);
            } catch (Exception e) {
                log.warn("알림 채널 전송 실패 (channel={}, mergedEventId={})",
                        channel.getName(), event.getMergedEventId(), e);
            }
        }
    }
}
