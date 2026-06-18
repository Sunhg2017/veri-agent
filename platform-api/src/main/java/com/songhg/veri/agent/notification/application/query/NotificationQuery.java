package com.songhg.veri.agent.notification.application.query;

public record NotificationQuery(
        String status,
        int index,
        int size
) {

    public static NotificationQuery of(String status, int index, int size) {
        return new NotificationQuery(status, index, size);
    }

    public int offset() {
        return index * size;
    }
}
