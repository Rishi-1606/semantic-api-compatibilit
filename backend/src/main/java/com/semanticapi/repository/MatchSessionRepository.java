package com.semanticapi.repository;

import com.semanticapi.model.MatchSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchSessionRepository extends JpaRepository<MatchSession, Long> {

    List<MatchSession> findBySourceVersionIdOrTargetVersionIdOrderByCreatedAtDesc(
            Long sourceVersionId, Long targetVersionId);

    List<MatchSession> findBySourceVersionIdAndTargetVersionIdOrderByCreatedAtDesc(
            Long sourceVersionId, Long targetVersionId);
}
