package com.gograbbit.controller;

import com.gograbbit.domain.WatchedRepo;
import com.gograbbit.dto.WatchedRepoRequest;
import com.gograbbit.dto.WatchedRepoResponse;
import com.gograbbit.dto.WatchedRepoUpdateRequest;
import com.gograbbit.repository.WatchedRepoRepository;
import com.gograbbit.service.PollerService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/repos")
public class RepoController {

    private final WatchedRepoRepository watchedRepoRepository;
    private final PollerService pollerService;

    public RepoController(WatchedRepoRepository watchedRepoRepository, PollerService pollerService) {
        this.watchedRepoRepository = watchedRepoRepository;
        this.pollerService = pollerService;
    }

    @GetMapping
    public List<WatchedRepoResponse> list() {
        return watchedRepoRepository.findAll().stream().map(WatchedRepoResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WatchedRepoResponse create(@Valid @RequestBody WatchedRepoRequest request) {
        WatchedRepo watchedRepo = new WatchedRepo(
                request.owner(),
                request.repo(),
                request.labels(),
                request.intervalMinutes() == null ? 30 : request.intervalMinutes()
        );
        return WatchedRepoResponse.from(watchedRepoRepository.save(watchedRepo));
    }

    @PatchMapping("/{id}")
    public WatchedRepoResponse update(@PathVariable UUID id, @Valid @RequestBody WatchedRepoUpdateRequest request) {
        WatchedRepo watchedRepo = findOrThrow(id);

        if (request.labels() != null) {
            watchedRepo.setLabels(request.labels());
        }
        if (request.intervalMinutes() != null) {
            watchedRepo.setIntervalMinutes(request.intervalMinutes());
        }
        if (request.active() != null) {
            watchedRepo.setActive(request.active());
        }

        return WatchedRepoResponse.from(watchedRepoRepository.save(watchedRepo));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        watchedRepoRepository.delete(findOrThrow(id));
    }

    @PostMapping("/{id}/poll")
    public ResponseEntity<PollResponse> pollNow(@PathVariable UUID id) {
        WatchedRepo watchedRepo = findOrThrow(id);
        int newIssueCount = pollerService.poll(watchedRepo);
        return ResponseEntity.ok(new PollResponse(newIssueCount));
    }

    private WatchedRepo findOrThrow(UUID id) {
        return watchedRepoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("WatchedRepo not found: " + id));
    }

    public record PollResponse(int newIssuesFound) {
    }
}
