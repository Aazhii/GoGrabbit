package com.gograbbit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class GitHubClientConfig {

    @Bean
    RestClient githubRestClient(
            @Value("${github.api-base-url}") String baseUrl,
            @Value("${github.token}") String token,
            @Value("${github.connect-timeout:5s}") Duration connectTimeout,
            @Value("${github.read-timeout:15s}") Duration readTimeout
    ) {
        // Without explicit timeouts a hung GitHub connection would block a request
        // thread indefinitely — and GitHubService retries three times, so an
        // unbounded call is an unbounded stall. Both are configurable in application.yml.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "GoGrabbit");

        if (token != null && !token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        return builder.build();
    }
}
