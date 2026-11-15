package com.astronaut.backend.notifications.repository;

import com.astronaut.backend.notifications.model.AlertState;
import com.astronaut.backend.notifications.model.HealthParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AlertStateRepository extends JpaRepository<AlertState, Long> {

    Optional<AlertState> findByAstronautIdAndParameter(Long astronautId, HealthParameter parameter);
}
