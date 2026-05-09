package io.github.chuseok22.erroralert.core.notification;

import io.github.chuseok22.erroralert.core.model.MergedErrorEvent;

public interface NotificationChannel {

    String getName();

    boolean isEnabled();

    void send(MergedErrorEvent event);
}
