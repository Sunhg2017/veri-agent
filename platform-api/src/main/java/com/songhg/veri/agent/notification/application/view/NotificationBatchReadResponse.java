package com.songhg.veri.agent.notification.application.view;

public record NotificationBatchReadResponse(
        long markedCount,
        long unreadCount
) {
}
