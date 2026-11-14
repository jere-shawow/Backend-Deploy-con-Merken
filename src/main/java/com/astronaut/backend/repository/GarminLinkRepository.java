package com.astronaut.backend.repository;

import com.astronaut.backend.model.GarminLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GarminLinkRepository extends JpaRepository<GarminLink, Long> {

    Optional<GarminLink> findByAstronautId(Long astronautId);

    void deleteByAstronautId(Long astronautId);
}
