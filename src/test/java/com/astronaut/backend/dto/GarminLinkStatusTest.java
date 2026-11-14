package com.astronaut.backend.dto;

import com.astronaut.backend.model.GarminLink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GarminLinkStatusTest {

    @Test
    @DisplayName("nunca expone la API key completa: solo los últimos 4 caracteres")
    void masksApiKey() {
        GarminLink link = new GarminLink(7L, "GCDC_abcdefghijklmnop1234");

        GarminLinkStatus status = GarminLinkStatus.of(link);

        assertThat(status.apiKeyHint()).isEqualTo("****1234");
        assertThat(status.toString()).doesNotContain("GCDC_abcdefghijklmnop1234");
        assertThat(status.linked()).isTrue();
        assertThat(status.astronautId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("keys muy cortas se ocultan por completo")
    void masksShortKeys() {
        assertThat(GarminLinkStatus.of(new GarminLink(1L, "abc")).apiKeyHint()).isEqualTo("****");
    }

    @Test
    @DisplayName("notLinked no trae datos del vínculo")
    void notLinked() {
        GarminLinkStatus status = GarminLinkStatus.notLinked(3L);

        assertThat(status.linked()).isFalse();
        assertThat(status.linkedAt()).isNull();
        assertThat(status.lastSyncAt()).isNull();
    }
}
