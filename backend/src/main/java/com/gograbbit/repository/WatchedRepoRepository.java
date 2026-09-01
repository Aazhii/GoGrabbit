package com.gograbbit.repository;

import com.gograbbit.domain.WatchedRepo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WatchedRepoRepository extends JpaRepository<WatchedRepo, UUID> {

    List<WatchedRepo> findByActiveTrue();
}
