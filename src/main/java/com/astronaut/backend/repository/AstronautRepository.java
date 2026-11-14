package com.astronaut.backend.repository;

import com.astronaut.backend.model.Astronaut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AstronautRepository extends JpaRepository<Astronaut, Long> {

    List<Astronaut> findAllByOrderByIdAsc();

    List<Astronaut> findByAstronautNameContainingIgnoreCase(String name);
}
