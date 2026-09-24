package com.pedeai.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String message,
        String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> fields
) {
    public ApiError {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    public static ApiError of(Instant timestamp, int status, String message, String path) {
        return new ApiError(timestamp, status, message, path, Map.of());
    }
}
