package com.astronaut.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Entidad que representa los datos de un astronauta.
 *
 * Unidades:
 *  - spaceRadiation    : mSv (milisievert)
 *  - distanceFromEarth : km
 *  - bloodPressure     : "sistólica/diastólica" en mmHg, ej. "120/80"
 *  - heartRate         : bpm
 *  - bloodSugar        : mg/dL
 *  - bodyTemperature   : °C
 *  - sleepHours        : horas
 *  - nutrition         : kcal/día (ingesta calórica diaria)
 *  - oxygenInBlood     : % SpO2 (saturación de oxígeno)
 *  - bodyBattery       : 0–100 (energía de Garmin; opcional, manual o desde Garmin)
 *  - hrv               : ms (variabilidad de la frecuencia cardíaca, promedio nocturno; opcional)
 */
@Entity
@Table(name = "astronauts")
public class Astronaut {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    @Column(name = "astronaut_name", nullable = false, length = 100)
    private String astronautName;

    @NotNull(message = "La radiación espacial es obligatoria")
    @DecimalMin(value = "0.0", message = "La radiación no puede ser negativa")
    @Column(name = "space_radiation", nullable = false)
    private Double spaceRadiation;

    @NotNull(message = "La distancia a la Tierra es obligatoria")
    @DecimalMin(value = "0.0", message = "La distancia no puede ser negativa")
    @Column(name = "distance_from_earth", nullable = false)
    private Double distanceFromEarth;

    @NotBlank(message = "La presión arterial es obligatoria")
    @Pattern(regexp = "^\\d{2,3}/\\d{2,3}$", message = "La presión arterial debe tener el formato 120/80")
    @Column(name = "blood_pressure", nullable = false, length = 10)
    private String bloodPressure;

    @NotNull(message = "La frecuencia cardíaca es obligatoria")
    @Min(value = 20, message = "Frecuencia cardíaca fuera de rango")
    @Max(value = 250, message = "Frecuencia cardíaca fuera de rango")
    @Column(name = "heart_rate", nullable = false)
    private Integer heartRate;

    @NotNull(message = "El azúcar en sangre es obligatorio")
    @DecimalMin(value = "0.0", message = "El azúcar en sangre no puede ser negativo")
    @Column(name = "blood_sugar", nullable = false)
    private Double bloodSugar;

    @NotNull(message = "La temperatura corporal es obligatoria")
    @DecimalMin(value = "30.0", message = "Temperatura corporal fuera de rango")
    @DecimalMax(value = "45.0", message = "Temperatura corporal fuera de rango")
    @Column(name = "body_temperature", nullable = false)
    private Double bodyTemperature;

    @NotNull(message = "Las horas de sueño son obligatorias")
    @DecimalMin(value = "0.0", message = "Las horas de sueño no pueden ser negativas")
    @DecimalMax(value = "24.0", message = "Las horas de sueño no pueden superar 24")
    @Column(name = "sleep_hours", nullable = false)
    private Double sleepHours;

    @NotNull(message = "La nutrición es obligatoria")
    @Min(value = 0, message = "La nutrición no puede ser negativa")
    @Max(value = 10000, message = "La nutrición no puede superar 10000 kcal")
    @Column(name = "nutrition")
    private Integer nutrition;

    @NotNull(message = "El oxígeno en sangre es obligatorio")
    @DecimalMin(value = "0.0", message = "El oxígeno en sangre no puede ser negativo")
    @DecimalMax(value = "100.0", message = "El oxígeno en sangre no puede superar 100%")
    @Column(name = "oxygen_in_blood")
    private Double oxygenInBlood;

    /** Opcional: se ingresa a mano o llega desde Garmin. */
    @Min(value = 0, message = "Body Battery no puede ser menor que 0")
    @Max(value = 100, message = "Body Battery no puede ser mayor que 100")
    @Column(name = "body_battery")
    private Integer bodyBattery;

    /** Opcional: VFC/HRV en milisegundos, manual o desde Garmin. */
    @DecimalMin(value = "0.0", message = "HRV no puede ser negativa")
    @DecimalMax(value = "300.0", message = "HRV fuera de rango (máximo 300 ms)")
    @Column(name = "hrv")
    private Double hrv;

    @NotNull(message = "El estado de ánimo es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "mood", nullable = false, length = 20)
    private Mood mood;

    /** Solo lectura desde la API: lo maneja el vínculo con Garmin. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "garmin_linked")
    private Boolean garminLinked = Boolean.FALSE;

    /** Última vez que HeartRate / SleepHours se actualizaron desde Garmin. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "garmin_last_sync")
    private Instant garminLastSync;

    public Astronaut() {
    }

    public Astronaut(String astronautName, Double spaceRadiation, Double distanceFromEarth,
                     String bloodPressure, Integer heartRate, Double bloodSugar,
                     Double bodyTemperature, Double sleepHours, Integer nutrition,
                     Double oxygenInBlood, Mood mood) {
        this.astronautName = astronautName;
        this.spaceRadiation = spaceRadiation;
        this.distanceFromEarth = distanceFromEarth;
        this.bloodPressure = bloodPressure;
        this.heartRate = heartRate;
        this.bloodSugar = bloodSugar;
        this.bodyTemperature = bodyTemperature;
        this.sleepHours = sleepHours;
        this.nutrition = nutrition;
        this.oxygenInBlood = oxygenInBlood;
        this.mood = mood;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAstronautName() { return astronautName; }
    public void setAstronautName(String astronautName) { this.astronautName = astronautName; }

    public Double getSpaceRadiation() { return spaceRadiation; }
    public void setSpaceRadiation(Double spaceRadiation) { this.spaceRadiation = spaceRadiation; }

    public Double getDistanceFromEarth() { return distanceFromEarth; }
    public void setDistanceFromEarth(Double distanceFromEarth) { this.distanceFromEarth = distanceFromEarth; }

    public String getBloodPressure() { return bloodPressure; }
    public void setBloodPressure(String bloodPressure) { this.bloodPressure = bloodPressure; }

    public Integer getHeartRate() { return heartRate; }
    public void setHeartRate(Integer heartRate) { this.heartRate = heartRate; }

    public Double getBloodSugar() { return bloodSugar; }
    public void setBloodSugar(Double bloodSugar) { this.bloodSugar = bloodSugar; }

    public Double getBodyTemperature() { return bodyTemperature; }
    public void setBodyTemperature(Double bodyTemperature) { this.bodyTemperature = bodyTemperature; }

    public Double getSleepHours() { return sleepHours; }
    public void setSleepHours(Double sleepHours) { this.sleepHours = sleepHours; }

    public Integer getNutrition() { return nutrition; }
    public void setNutrition(Integer nutrition) { this.nutrition = nutrition; }

    public Double getOxygenInBlood() { return oxygenInBlood; }
    public void setOxygenInBlood(Double oxygenInBlood) { this.oxygenInBlood = oxygenInBlood; }

    public Integer getBodyBattery() { return bodyBattery; }
    public void setBodyBattery(Integer bodyBattery) { this.bodyBattery = bodyBattery; }

    public Double getHrv() { return hrv; }
    public void setHrv(Double hrv) { this.hrv = hrv; }

    public Mood getMood() { return mood; }
    public void setMood(Mood mood) { this.mood = mood; }

    public Boolean getGarminLinked() { return garminLinked; }
    public void setGarminLinked(Boolean garminLinked) { this.garminLinked = garminLinked; }

    public Instant getGarminLastSync() { return garminLastSync; }
    public void setGarminLastSync(Instant garminLastSync) { this.garminLastSync = garminLastSync; }
}
