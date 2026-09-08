package com.gograbbit.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GitHubServiceTest {

    private final GitHubService service = new GitHubService(null, 100);

    @Test
    void retriesOnTransientNetworkFailureThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        String result = service.executeWithRetry(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new ResourceAccessException("connection reset");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void retriesOnServerErrorUpToMaxAttemptsThenThrows() {
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> service.executeWithRetry(() -> {
            attempts.incrementAndGet();
            throw HttpServerErrorException.create(
                    HttpStatus.BAD_GATEWAY, "Bad Gateway", null, null, null);
        })).isInstanceOf(HttpServerErrorException.class);

        assertEquals(3, attempts.get());
    }

    @Test
    void doesNotRetryClientErrors() {
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> service.executeWithRetry(() -> {
            attempts.incrementAndGet();
            throw HttpClientErrorException.create(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", null, null, null);
        })).isInstanceOf(HttpClientErrorException.class);

        assertEquals(1, attempts.get());
    }
}
