package com.rewin.itsm.web;

import com.rewin.itsm.common.PageResponse;
import com.rewin.itsm.dto.notification.NotificationResponse;
import com.rewin.itsm.security.AppUserPrincipal;
import com.rewin.itsm.service.NotificationService;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A user's own notifications.
 *
 * <p>There is no recipient parameter anywhere here. The caller is taken from the
 * token, so these endpoints can only ever act on the caller's own notifications
 * - which is what stops one user reading another's by guessing an id.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @PageableDefault(size = 50) Pageable pageable,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return notificationService.list(principal, pageable);
    }

    /** The bell's badge, kept separate so it can be polled cheaply. */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AppUserPrincipal principal) {
        return Map.of("unread", notificationService.unreadCount(principal));
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id,
                                         @AuthenticationPrincipal AppUserPrincipal principal) {
        return notificationService.markRead(id, principal);
    }

    @PatchMapping("/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AppUserPrincipal principal) {
        return Map.of("updated", notificationService.markAllRead(principal));
    }
}
