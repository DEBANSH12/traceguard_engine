package com.debansh.traceguard.repository;

import com.debansh.traceguard.entity.IncidentGroup;
import com.debansh.traceguard.entity.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface IncidentGroupRepository extends JpaRepository<IncidentGroup, Long> {

    Optional<IncidentGroup> findByFingerprint(String fingerprint);

    List<IncidentGroup> findAllByOrderByLastSeenAtDesc();

    List<IncidentGroup> findByStatusOrderByLastSeenAtDesc(IncidentStatus status);

    @Query("SELECT COALESCE(SUM(i.occurrenceCount), 0) FROM IncidentGroup i")
    long sumTotalOccurrences();
}
