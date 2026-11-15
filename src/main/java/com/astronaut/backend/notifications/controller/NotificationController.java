package com.astronaut.backend.notifications.controller;

import com.astronaut.backend.notifications.dto.NotificationRequest;
import com.astronaut.backend.notifications.dto.UnreadCount;
import com.astronaut.backend.notifications.model.Notification;
import com.astronaut.backend.notifications.service.MonitoringService;
import com.astronaut.backend.notifications.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 *  GET    /api/notifications?astronautId=&unreadOnly=&includeExpired=
 *  GET    /api/notifications/{id}
 *  GET    /api/notifications/unread-count?astronautId=
 *  POST   /api/notifications                 crear manual
 *  PATCH  /api/notifications/{id}/read       marcar leída
 *  POST   /api/notifications/read-all?astronautId=
 *  DELETE /api/notifications/{id}
 *  POST   /api/notifications/evaluate        revisar niveles ahora (sin esperar al ciclo)
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;
    private final MonitoringService monitoringService;

    public NotificationController(NotificationService service, MonitoringService monitoringService) {
        this.service = service;
        this.monitoringService = monitoringService;
    }

    @GetMapping
    public List<Notification> list(@RequestParam(required = false) Long astronautId,
                                   @RequestParam(defaultValue = "false") boolean unreadOnly,
                                   @RequestParam(defaultValue = "false") boolean includeExpired) {
        return service.search(astronautId, unreadOnly, includeExpired);
    }

    @GetMapping("/{id}")
    public Notification get(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/unread-count")
    public UnreadCount unreadCount(@RequestParam(required = false) Long astronautId) {
        return new UnreadCount(astronautId, service.countUnread(astronautId));
    }

    @PostMapping
    public ResponseEntity<Notification> create(@Valid @RequestBody NotificationRequest request) {
        Notification created = service.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}").buildAndExpand(created.getId()).toUri())
                .body(created);
    }

    @PatchMapping("/{id}/read")
    public Notification markRead(@PathVariable Long id) {
        return service.markRead(id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(@RequestParam(required = false) Long astronautId) {
        return Map.of("updated", service.markAllRead(astronautId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/evaluate")
    public Map<String, Integer> evaluateNow() {
        return Map.of("created", monitoringService.runCycle());
    }
}
