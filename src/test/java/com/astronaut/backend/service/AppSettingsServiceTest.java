package com.astronaut.backend.service;

import com.astronaut.backend.model.AppSettings;
import com.astronaut.backend.repository.AppSettingsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppSettingsServiceTest {

    @Mock
    private AppSettingsRepository repository;

    @InjectMocks
    private AppSettingsService service;

    @Test
    @DisplayName("si no hay configuración guardada se crea con 5 segundos")
    void createsDefaultSettings() {
        when(repository.findById(AppSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        when(repository.save(any(AppSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        AppSettings settings = service.get();

        assertThat(settings.getId()).isEqualTo(AppSettings.SINGLETON_ID);
        assertThat(settings.getRefreshIntervalSeconds()).isEqualTo(5);
    }

    @Test
    @DisplayName("update guarda el nuevo intervalo y lo deja en memoria")
    void updateCachesInterval() {
        AppSettings stored = new AppSettings();
        when(repository.findById(AppSettings.SINGLETON_ID)).thenReturn(Optional.of(stored));
        when(repository.save(any(AppSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        AppSettings data = new AppSettings();
        data.setRefreshIntervalSeconds(30);
        service.update(data);

        assertThat(stored.getRefreshIntervalSeconds()).isEqualTo(30);
        // las siguientes lecturas no consultan la base
        assertThat(service.getRefreshIntervalSeconds()).isEqualTo(30);
        assertThat(service.getRefreshIntervalSeconds()).isEqualTo(30);
        verify(repository, times(1)).findById(AppSettings.SINGLETON_ID);
    }

    @Test
    @DisplayName("getRefreshIntervalSeconds carga desde la base la primera vez")
    void loadsIntervalLazily() {
        AppSettings stored = new AppSettings();
        stored.setRefreshIntervalSeconds(12);
        when(repository.findById(AppSettings.SINGLETON_ID)).thenReturn(Optional.of(stored));

        assertThat(service.getRefreshIntervalSeconds()).isEqualTo(12);
        assertThat(service.getRefreshIntervalSeconds()).isEqualTo(12);
        verify(repository, times(1)).findById(AppSettings.SINGLETON_ID);
    }
}
