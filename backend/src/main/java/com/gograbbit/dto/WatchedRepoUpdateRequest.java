package com.gograbbit.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WatchedRepoUpdateRequest(
        @Size(min = 1) List<String> labels,
        @Min(1) Integer intervalMinutes,
        Boolean active
) {
}
