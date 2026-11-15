package com.astronaut.backend.service;

import com.astronaut.backend.dto.GarminLinkStatus;
import com.astronaut.backend.exception.ResourceNotFoundException;
import com.astronaut.backend.garmin.GarminApiClient;
import com.astronaut.backend.garmin.GarminApiClient.AccessToken;
import com.astronaut.backend.garmin.GarminException;
import com.astronaut.backend.model.Astronaut;
import com.astronaut.backend.model.GarminLink;
import com.astronaut.backend.repository.AstronautRepository;
import com.astronaut.backend.repository.GarminLinkRepository;
import com.astronaut.backend.support.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GarminSyncServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final JsonNode HR_JSON = JsonNodeFactory.instance.objectNode();
    private static final JsonNode SLEEP_JSON = JsonNodeFactory.instance.objectNode();

    @Mock private GarminLinkRepository linkRepository;
    @Mock private AstronautRepository astronautRepository;
    @Mock private GarminApiClient garmin;

    private GarminSyncService service;
    private Astronaut astronaut;
    private GarminLink link;
    private AccessToken token;

    @BeforeEach
    void setUp() {
        service = new GarminSyncService(linkRepository, astronautRepository, garmin, 10, 60);

        astronaut = TestData.astronaut("Valentina");
        astronaut.setId(1L);
        astronaut.setGarminLinked(false);
        link = new GarminLink(1L, "GCDC_key");

        token = new AccessToken("jwt", Instant.now().plusSeconds(3600));

        when(astronautRepository.findById(1L)).thenReturn(Optional.of(astronaut));
        when(astronautRepository.save(any(Astronaut.class))).thenAnswer(inv -> inv.getArgument(0));
        when(linkRepository.save(any(GarminLink.class))).thenAnswer(inv -> inv.getArgument(0));
        when(linkRepository.findByAstronautId(1L)).thenReturn(Optional.of(link));

        when(garmin.today()).thenReturn(TODAY);
        when(garmin.obtainToken(anyString())).thenReturn(token);
        when(garmin.getDailyHeartRate(any(), eq(TODAY))).thenReturn(HR_JSON);
        when(garmin.getDailySleep(any(), eq(TODAY))).thenReturn(SLEEP_JSON);
        when(garmin.latestHeartRate(HR_JSON)).thenReturn(Optional.of(97));
        when(garmin.sleepHours(SLEEP_JSON)).thenReturn(Optional.of(6.5));
    }

    @Test
    @DisplayName("link valida la key, guarda el vínculo y sincroniza pulso y sueño al instante")
    void linkSyncsImmediately() {
        when(linkRepository.findByAstronautId(1L)).thenReturn(Optional.empty());

        GarminLinkStatus status = service.link(1L, " GCDC_key ");

        assertThat(status.linked()).isTrue();
        assertThat(status.lastError()).isNull();
        assertThat(astronaut.getHeartRate()).isEqualTo(97);
        assertThat(astronaut.getSleepHours()).isEqualTo(6.5);
        assertThat(astronaut.getGarminLinked()).isTrue();
        assertThat(astronaut.getGarminLastSync()).isNotNull();
        verify(garmin, times(1)).obtainToken(" GCDC_key "); // el token obtenido al validar se reutiliza
    }

    @Test
    @DisplayName("link con key inválida no guarda nada")
    void linkWithInvalidKeyDoesNotPersist() {
        when(garmin.obtainToken("bad")).thenThrow(new GarminException("rechazada", true));

        assertThatThrownBy(() -> service.link(1L, "bad")).isInstanceOf(GarminException.class);

        verify(linkRepository, never()).save(any());
        assertThat(astronaut.getGarminLinked()).isFalse();
    }

    @Test
    @DisplayName("link a un astronauta inexistente -> 404")
    void linkUnknownAstronaut() {
        when(astronautRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link(9L, "GCDC_key")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("syncAll actualiza a cada astronauta vinculado")
    void syncAllUpdatesLinkedAstronauts() {
        when(linkRepository.findAll()).thenReturn(List.of(link));

        service.syncAll();

        assertThat(astronaut.getHeartRate()).isEqualTo(97);
        assertThat(astronaut.getSleepHours()).isEqualTo(6.5);
        assertThat(link.getLastSyncAt()).isNotNull();
        assertThat(link.getLastError()).isNull();
    }

    @Test
    @DisplayName("el sueño se consulta como máximo cada N minutos; el pulso en cada vuelta")
    void sleepIsCachedHeartRateIsNot() {
        when(linkRepository.findAll()).thenReturn(List.of(link));

        service.syncAll();
        service.syncAll();
        service.syncAll();

        verify(garmin, times(3)).getDailyHeartRate(any(), eq(TODAY));
        verify(garmin, times(1)).getDailySleep(any(), eq(TODAY));
    }

    @Test
    @DisplayName("Body Battery (máx. 1 vez por minuto) y HRV (junto con el sueño) llegan desde Garmin")
    void bodyBatteryAndHrv() {
        JsonNode bbJson = JsonNodeFactory.instance.arrayNode();
        JsonNode hrvJson = JsonNodeFactory.instance.objectNode();
        when(linkRepository.findAll()).thenReturn(List.of(link));
        when(garmin.getDailyBodyBattery(any(), eq(TODAY))).thenReturn(bbJson);
        when(garmin.getDailyHrv(any(), eq(TODAY))).thenReturn(hrvJson);
        when(garmin.latestBodyBattery(bbJson)).thenReturn(Optional.of(55));
        when(garmin.hrvLastNight(hrvJson)).thenReturn(Optional.of(42.0));

        service.syncAll();
        service.syncAll();

        assertThat(astronaut.getBodyBattery()).isEqualTo(55);
        assertThat(astronaut.getHrv()).isEqualTo(42.0);
        verify(garmin, times(1)).getDailyBodyBattery(any(), eq(TODAY));
        verify(garmin, times(1)).getDailyHrv(any(), eq(TODAY));
        verify(garmin, times(2)).getDailyHeartRate(any(), eq(TODAY));
    }

    @Test
    @DisplayName("Body Battery fuera de 0–100 o HRV fuera de rango se ignoran")
    void ignoresImpossibleBodyBatteryAndHrv() {
        when(linkRepository.findAll()).thenReturn(List.of(link));
        astronaut.setBodyBattery(50);
        astronaut.setHrv(40.0);
        JsonNode bbJson = JsonNodeFactory.instance.arrayNode();
        JsonNode hrvJson = JsonNodeFactory.instance.objectNode();
        when(garmin.getDailyBodyBattery(any(), eq(TODAY))).thenReturn(bbJson);
        when(garmin.getDailyHrv(any(), eq(TODAY))).thenReturn(hrvJson);
        when(garmin.latestBodyBattery(bbJson)).thenReturn(Optional.of(150));
        when(garmin.hrvLastNight(hrvJson)).thenReturn(Optional.of(900.0));

        service.syncAll();

        assertThat(astronaut.getBodyBattery()).isEqualTo(50);
        assertThat(astronaut.getHrv()).isEqualTo(40.0);
    }

    @Test
    @DisplayName("el token se reutiliza entre vueltas (no se pide uno por consulta)")
    void tokenIsCached() {
        when(linkRepository.findAll()).thenReturn(List.of(link));

        service.syncAll();
        service.syncAll();

        verify(garmin, times(1)).obtainToken("GCDC_key");
    }

    @Test
    @DisplayName("si Garmin responde 401 se pide un token nuevo y se reintenta una vez")
    void renewsTokenOnAuthError() {
        when(linkRepository.findAll()).thenReturn(List.of(link));
        AccessToken fresh = new AccessToken("fresh", Instant.now().plusSeconds(3600));
        when(garmin.obtainToken("GCDC_key")).thenReturn(token, fresh);
        when(garmin.getDailyHeartRate(token, TODAY)).thenThrow(new GarminException("401", true));
        when(garmin.getDailyHeartRate(fresh, TODAY)).thenReturn(HR_JSON);

        service.syncAll();

        verify(garmin, times(2)).obtainToken("GCDC_key");
        assertThat(astronaut.getHeartRate()).isEqualTo(97);
        assertThat(link.getLastError()).isNull();
    }

    @Test
    @DisplayName("un error de Garmin queda registrado en el vínculo y no detiene a los demás")
    void errorIsRecordedPerLink() {
        Astronaut other = TestData.astronaut("Mateo");
        other.setId(2L);
        GarminLink otherLink = new GarminLink(2L, "GCDC_other");
        when(astronautRepository.findById(2L)).thenReturn(Optional.of(other));
        when(linkRepository.findAll()).thenReturn(List.of(link, otherLink));
        when(garmin.obtainToken("GCDC_key")).thenThrow(new GarminException("Garmin caído", false));

        service.syncAll();

        assertThat(link.getLastError()).isEqualTo("Garmin caído");
        assertThat(other.getHeartRate()).isEqualTo(97);
        assertThat(otherLink.getLastError()).isNull();
    }

    @Test
    @DisplayName("sin datos del día se informa en lastError pero no se borra nada")
    void noDataToday() {
        when(linkRepository.findAll()).thenReturn(List.of(link));
        when(garmin.latestHeartRate(HR_JSON)).thenReturn(Optional.empty());
        when(garmin.sleepHours(SLEEP_JSON)).thenReturn(Optional.empty());

        service.syncAll();

        assertThat(astronaut.getHeartRate()).isEqualTo(72); // valor previo intacto
        assertThat(link.getLastError()).contains("no tiene datos");
    }

    @Test
    @DisplayName("valores fuera de rango fisiológico se ignoran")
    void ignoresImpossibleValues() {
        when(linkRepository.findAll()).thenReturn(List.of(link));
        when(garmin.latestHeartRate(HR_JSON)).thenReturn(Optional.of(400));
        when(garmin.sleepHours(SLEEP_JSON)).thenReturn(Optional.of(30.0));

        service.syncAll();

        assertThat(astronaut.getHeartRate()).isEqualTo(72);
        assertThat(astronaut.getSleepHours()).isEqualTo(7.5);
    }

    @Test
    @DisplayName("si el astronauta ya no existe, el vínculo huérfano se elimina")
    void orphanLinkIsDeleted() {
        when(linkRepository.findAll()).thenReturn(List.of(link));
        when(astronautRepository.findById(1L)).thenReturn(Optional.empty());

        service.syncAll();

        verify(linkRepository).delete(link);
    }

    @Test
    @DisplayName("unlink borra el vínculo y marca al astronauta como no vinculado")
    void unlink() {
        astronaut.setGarminLinked(true);

        service.unlink(1L);

        verify(linkRepository).deleteByAstronautId(1L);
        assertThat(astronaut.getGarminLinked()).isFalse();
    }

    @Test
    @DisplayName("status sin vínculo -> linked=false")
    void statusNotLinked() {
        when(linkRepository.findByAstronautId(1L)).thenReturn(Optional.empty());

        GarminLinkStatus status = service.status(1L);

        assertThat(status.linked()).isFalse();
        assertThat(status.apiKeyHint()).isNull();
    }

    @Test
    @DisplayName("syncNow sin vínculo -> 404")
    void syncNowWithoutLink() {
        when(linkRepository.findByAstronautId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncNow(1L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
