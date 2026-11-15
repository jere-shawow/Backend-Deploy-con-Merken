package com.astronaut.backend.service;

import com.astronaut.backend.exception.ResourceNotFoundException;
import com.astronaut.backend.model.Astronaut;
import com.astronaut.backend.model.Mood;
import com.astronaut.backend.repository.AstronautRepository;
import com.astronaut.backend.repository.GarminLinkRepository;
import com.astronaut.backend.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AstronautServiceTest {

    @Mock
    private AstronautRepository repository;

    @Mock
    private GarminLinkRepository garminLinkRepository;

    @InjectMocks
    private AstronautService service;

    private Astronaut existing;

    @BeforeEach
    void setUp() {
        existing = TestData.astronaut("Valentina");
        existing.setId(1L);
        existing.setGarminLinked(true);
        existing.setGarminLastSync(Instant.parse("2026-11-14T12:00:00Z"));
    }

    @Test
    @DisplayName("findAll devuelve la lista ordenada del repositorio")
    void findAllDelegatesToOrderedQuery() {
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(existing));

        assertThat(service.findAll()).containsExactly(existing);
    }

    @Test
    @DisplayName("findById lanza ResourceNotFoundException si no existe")
    void findByIdThrowsWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("create ignora el id recibido y parte sin vínculo Garmin")
    void createResetsIdAndGarminState() {
        Astronaut input = TestData.astronaut("Nuevo");
        input.setId(123L);
        input.setGarminLinked(true);
        input.setGarminLastSync(Instant.now());
        when(repository.save(any(Astronaut.class))).thenAnswer(inv -> inv.getArgument(0));

        Astronaut created = service.create(input);

        assertThat(created.getId()).isNull();
        assertThat(created.getGarminLinked()).isFalse();
        assertThat(created.getGarminLastSync()).isNull();
    }

    @Test
    @DisplayName("update copia todos los datos editables pero no toca el estado Garmin")
    void updateCopiesEditableFieldsOnly() {
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Astronaut.class))).thenAnswer(inv -> inv.getArgument(0));

        Astronaut data = new Astronaut("Valentina R.", 1.5, 384_400.0, "130/85", 95, 120.0,
                37.8, 5.0, 3000, 95.0, Mood.CANSADO);
        data.setGarminLinked(false);
        data.setBodyBattery(35);
        data.setHrv(41.5);

        Astronaut updated = service.update(1L, data);

        assertThat(updated.getBodyBattery()).isEqualTo(35);
        assertThat(updated.getHrv()).isEqualTo(41.5);

        assertThat(updated.getAstronautName()).isEqualTo("Valentina R.");
        assertThat(updated.getSpaceRadiation()).isEqualTo(1.5);
        assertThat(updated.getDistanceFromEarth()).isEqualTo(384_400.0);
        assertThat(updated.getBloodPressure()).isEqualTo("130/85");
        assertThat(updated.getHeartRate()).isEqualTo(95);
        assertThat(updated.getBloodSugar()).isEqualTo(120.0);
        assertThat(updated.getBodyTemperature()).isEqualTo(37.8);
        assertThat(updated.getSleepHours()).isEqualTo(5.0);
        assertThat(updated.getNutrition()).isEqualTo(3000);
        assertThat(updated.getOxygenInBlood()).isEqualTo(95.0);
        assertThat(updated.getMood()).isEqualTo(Mood.CANSADO);
        // estado Garmin intacto
        assertThat(updated.getGarminLinked()).isTrue();
        assertThat(updated.getGarminLastSync()).isEqualTo(Instant.parse("2026-11-14T12:00:00Z"));
    }

    @Test
    @DisplayName("update de un id inexistente no guarda nada")
    void updateMissingDoesNotSave() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(5L, TestData.astronaut("x")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("delete elimina primero el vínculo Garmin y luego el astronauta")
    void deleteRemovesGarminLinkFirst() {
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        var order = inOrder(garminLinkRepository, repository);
        order.verify(garminLinkRepository).deleteByAstronautId(1L);
        order.verify(repository).delete(existing);
    }

    @Test
    @DisplayName("searchByName delega en la búsqueda sin distinguir mayúsculas")
    void searchByName() {
        when(repository.findByAstronautNameContainingIgnoreCase("val")).thenReturn(List.of(existing));

        assertThat(service.searchByName("val")).containsExactly(existing);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(repository).findByAstronautNameContainingIgnoreCase(captor.capture());
        assertThat(captor.getValue()).isEqualTo("val");
    }
}
