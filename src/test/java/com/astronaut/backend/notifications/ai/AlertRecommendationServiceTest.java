package com.astronaut.backend.notifications.ai;

import com.astronaut.backend.analysis.AnalysisService;
import com.astronaut.backend.analysis.AnalysisTypes.Result;
import com.astronaut.backend.analysis.AnalysisTypes.StoredRequest;
import com.astronaut.backend.exception.ServiceUnavailableException;
import com.astronaut.backend.notifications.ai.AlertRecommendationService.RecommendationException;
import com.astronaut.backend.notifications.model.AlertLevel;
import com.astronaut.backend.notifications.model.AlertState;
import com.astronaut.backend.notifications.model.HealthParameter;
import com.astronaut.backend.notifications.model.Notification;
import com.astronaut.backend.notifications.repository.AlertStateRepository;
import com.astronaut.backend.notifications.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertRecommendationServiceTest {

    private final NotificationService notifications = mock(NotificationService.class);
    private final AlertStateRepository states = mock(AlertStateRepository.class);
    private final AnalysisService analysis = mock(AnalysisService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC);
    private final Result result = new Result("AWS_BEDROCK", "us.amazon.nova-pro-v1:0", "en", "GENERATED",
            null, null, List.of(), null, "disclaimer", false);

    private AlertRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new AlertRecommendationService(notifications, states, analysis, true, 24, clock);
        when(notifications.findById(1L)).thenReturn(alert(1L, 7L, AlertLevel.HIGH));
        when(notifications.findById(2L)).thenReturn(alert(2L, 7L, AlertLevel.HIGH));
        when(analysis.stored(eq(7L), any())).thenReturn(result);
    }

    private static Notification alert(long id, Long astronautId, AlertLevel level) {
        Notification n = new Notification();
        n.setId(id);
        n.setAstronautId(astronautId);
        n.setParameter(HealthParameter.PULSE);
        n.setLevel(level);
        return n;
    }

    private void currentAlerts(AlertLevel pulseLevel) {
        when(states.findByAstronautId(7L)).thenReturn(List.of(
                new AlertState(7L, HealthParameter.PULSE, pulseLevel, clock.instant())));
    }

    @Test
    @DisplayName("primera vez: llama a Nova con el envío autorizado")
    void firstCallInvokesNova() {
        currentAlerts(AlertLevel.HIGH);

        var r = service.forNotification(1L);

        assertThat(r.cached()).isFalse();
        assertThat(r.analysis()).isSameAs(result);
        verify(analysis).stored(7L, new StoredRequest(true, null));
    }

    @Test
    @DisplayName("repetición de la misma alerta: reutiliza la recomendación (sin otra llamada)")
    void repeatsReuse() {
        currentAlerts(AlertLevel.HIGH);
        service.forNotification(1L);

        var again = service.forNotification(2L);

        assertThat(again.cached()).isTrue();
        verify(analysis, times(1)).stored(eq(7L), any());
    }

    @Test
    @DisplayName("cambio de nivel (alto -> muy alto): nueva llamada a Nova")
    void levelChangeCallsAgain() {
        currentAlerts(AlertLevel.HIGH);
        service.forNotification(1L);

        currentAlerts(AlertLevel.VERY_HIGH);
        var r = service.forNotification(1L);

        assertThat(r.cached()).isFalse();
        verify(analysis, times(2)).stored(eq(7L), any());
    }

    @Test
    @DisplayName("desactivado: 503 sin llamar a Nova")
    void disabled() {
        service = new AlertRecommendationService(notifications, states, analysis, false, 24, clock);

        assertThatThrownBy(() -> service.forNotification(1L))
                .isInstanceOf(RecommendationException.class)
                .extracting(e -> ((RecommendationException) e).status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(analysis, never()).stored(anyLong(), any());
    }

    @Test
    @DisplayName("notificación manual: 400")
    void manualNotification() {
        when(notifications.findById(3L)).thenReturn(alert(3L, null, null));

        assertThatThrownBy(() -> service.forNotification(3L))
                .isInstanceOf(RecommendationException.class)
                .extracting(e -> ((RecommendationException) e).status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Nova ocupado (429) o caído (503): error claro y no se guarda nada en caché")
    void novaErrors() {
        currentAlerts(AlertLevel.HIGH);
        when(analysis.stored(eq(7L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "busy"))
                .thenThrow(new ServiceUnavailableException("Ground analysis is disabled", null))
                .thenReturn(result);

        assertThatThrownBy(() -> service.forNotification(1L))
                .extracting(e -> ((RecommendationException) e).status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThatThrownBy(() -> service.forNotification(1L))
                .hasMessageContaining("Ground analysis is disabled");
        assertThat(service.forNotification(1L).cached()).isFalse();
    }
}
