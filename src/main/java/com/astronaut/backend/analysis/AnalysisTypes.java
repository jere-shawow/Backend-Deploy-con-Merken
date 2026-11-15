package com.astronaut.backend.analysis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class AnalysisTypes {
    private AnalysisTypes() {}
    public record Reading(@NotBlank @Size(max=40) String metric, Double value,
                          @NotBlank @Size(max=30) String unit, Instant measuredAt,
                          @NotBlank @Size(max=50) String source) {}
    public record Preview(@NotNull @Size(min=1,max=16) List<@Valid Reading> readings) {}
    public record StoredRequest(boolean allowRemoteAnalysis,
                                @Size(max=100) String reportId) {}
    public record Snapshot(Instant storedAt, List<Reading> readings) {}
    public record Context(List<Reading> readings, List<Snapshot> history,
                          String quality, String freshness, String exerciseClearance,
                          List<String> existingAlerts, String coreAction) {}
    public record Observation(String metric, String explanation) {}
    public record Narrative(String summary, List<Observation> observations,
                            List<String> uncertainties, List<String> activityIds,
                            List<String> sourceIds) {}
    public record Activity(String id, String recommendation, String sourceId) {}
    public record Usage(Integer inputTokens, Integer outputTokens, long elapsedMs) {}
    public record Result(String provider, String modelId, String language, String status,
                         Context context, Narrative narrative, List<Activity> activities,
                         Usage usage, String disclaimer, boolean clinicallyValidated) {}
}
