package com.gograbbit.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final boolean githubAuthenticated;

    public GlobalExceptionHandler(@Value("${github.token:}") String githubToken) {
        this.githubAuthenticated = githubToken != null && !githubToken.isBlank();
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("That repo is already being watched."));
    }

    /** Invalid / mutually-exclusive request parameters (see {@code IssueSearchCriteria.of}). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    /**
     * 4xx from GitHub. {@code HttpClientErrorException} is a subclass of
     * {@code RestClientException}; Spring dispatches to the most specific
     * handler, so this wins over {@link #handleUpstreamFailure} for 4xx and the
     * generic 502 path still covers everything else.
     *
     * <p>Rate limiting (403 or 429 with an exhausted bucket, or a secondary-limit
     * {@code retry-after}) is surfaced as a 429 with {@code Retry-After}, because a
     * generic 502 tells the caller nothing actionable.
     */
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleGitHubClientError(HttpClientErrorException ex) {
        HttpStatusCode status = ex.getStatusCode();
        HttpHeaders headers = ex.getResponseHeaders();

        if (isRateLimited(status, headers)) {
            Integer retryAfter = retryAfterSeconds(headers);
            String message = rateLimitMessage(headers, retryAfter);
            log.warn("GitHub rate limit hit: status={} retryAfter={}s", status.value(), retryAfter);

            ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
            if (retryAfter != null) {
                builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            }
            return builder.body(new ErrorResponse(message, retryAfter));
        }

        log.warn("GitHub API returned {}: {}", status.value(), ex.getResponseBodyAsString());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("GitHub rejected the request (HTTP " + status.value() + "): "
                        + githubMessage(ex)));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponse> handleUpstreamFailure(RestClientException ex) {
        log.warn("GitHub API call failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("GitHub API request failed: " + ex.getMessage()));
    }

    private static boolean isRateLimited(HttpStatusCode status, HttpHeaders headers) {
        if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return true;
        }
        if (status.value() != HttpStatus.FORBIDDEN.value()) {
            return false;
        }
        if (headers == null) {
            return false;
        }
        // Primary limit: remaining == 0. Secondary limit: a retry-after header.
        String remaining = headers.getFirst("x-ratelimit-remaining");
        if ("0".equals(remaining == null ? null : remaining.trim())) {
            return true;
        }
        return headers.getFirst(HttpHeaders.RETRY_AFTER) != null;
    }

    private static Integer retryAfterSeconds(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                return Math.max(0, Integer.parseInt(retryAfter.trim()));
            } catch (NumberFormatException ignored) {
                // fall through to the reset header
            }
        }
        String reset = headers.getFirst("x-ratelimit-reset");
        if (reset != null) {
            try {
                long resetEpochSeconds = Long.parseLong(reset.trim());
                long seconds = Duration.between(Instant.now(), Instant.ofEpochSecond(resetEpochSeconds)).toSeconds();
                return (int) Math.max(0, seconds);
            } catch (NumberFormatException | ArithmeticException ignored) {
                return null;
            }
        }
        return null;
    }

    private String rateLimitMessage(HttpHeaders headers, Integer retryAfter) {
        String resource = headers == null ? null : headers.getFirst("x-ratelimit-resource");
        StringBuilder sb = new StringBuilder("GitHub rate limit exceeded");
        if (resource != null) {
            sb.append(" (").append(resource.toLowerCase(Locale.ROOT)).append(" bucket)");
        }
        sb.append(". ");
        if ("search".equalsIgnoreCase(resource)) {
            sb.append("GitHub's search API allows 10 requests/minute unauthenticated and 30/minute authenticated. ");
        } else {
            sb.append("GitHub allows 60 requests/hour unauthenticated and 5000/hour authenticated. ");
        }
        sb.append(githubAuthenticated
                ? "This server is using an authenticated token."
                : "This server is running UNAUTHENTICATED — set GITHUB_TOKEN to get the higher limit.");
        if (retryAfter != null) {
            sb.append(" Retry in about ").append(retryAfter).append(" second(s).");
        }
        return sb.toString();
    }

    private static String githubMessage(HttpClientErrorException ex) {
        String body = ex.getResponseBodyAsString();
        return (body == null || body.isBlank()) ? ex.getStatusText() : body;
    }

    /**
     * @param retryAfterSeconds only set on 429 responses; omitted from the JSON otherwise
     *                          so the existing 404/409/502 shapes are unchanged.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(String message, Integer retryAfterSeconds) {
        public ErrorResponse(String message) {
            this(message, null);
        }
    }
}
