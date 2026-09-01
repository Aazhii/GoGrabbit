package com.gograbbit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class GitHubClientConfig {

    @Bean
    RestClient githubRestClient(
            @Value("${github.api-base-url}") String baseUrl,
            @Value("${github.token}") String token
    ) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(org.springframework.http.HttpHeaders.USER_AGENT, "GoGrabbit");

        if (token != null && !token.isBlank()) {
            builder.defaultHeader(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        return builder.build();
    }
}
