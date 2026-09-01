package com.gograbbit.dto;

import jakarta.validation.constraints.Min;

import java.util.List;

public record WatchedRepoUpdateRequest(
        List<String> labels,
        @Min(1) Integer intervalMinutes,
        Boolean active
) {
}
