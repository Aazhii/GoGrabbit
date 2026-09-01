package com.gograbbit.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record WatchedRepoRequest(
        @NotBlank String owner,
        @NotBlank String repo,
        @NotEmpty List<String> labels,
        @Min(1) Integer intervalMinutes
) {
}
