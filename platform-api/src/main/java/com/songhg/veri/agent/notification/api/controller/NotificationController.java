package com.songhg.veri.agent.notification.api.controller;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.common.openapi.ApiVersion;
import com.songhg.veri.agent.notification.application.UserNotificationService;
import com.songhg.veri.agent.notification.application.query.NotificationListRequest;
import com.songhg.veri.agent.notification.application.view.NotificationBatchReadResponse;
import com.songhg.veri.agent.notification.application.view.UnreadNotificationCountResponse;
import com.songhg.veri.agent.notification.application.view.UserNotificationResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiVersion
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final UserNotificationService service;

    public NotificationController(UserNotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<UserNotificationResponse> notifications(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @Valid NotificationListRequest request
    ) {
        return service.notifications(principal, request);
    }

    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse unreadCount(
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return service.unreadCount(principal);
    }

    @PostMapping("/{id}/read")
    public UserNotificationResponse markRead(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @PathVariable UUID id
    ) {
        return service.markRead(principal, id);
    }

    @PostMapping("/read-all")
    public NotificationBatchReadResponse markAllRead(
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return service.markAllRead(principal);
    }
}
