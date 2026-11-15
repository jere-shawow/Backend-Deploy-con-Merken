package com.astronaut.backend.notifications.repository;

import com.astronaut.backend.notifications.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Filtro combinable: por usuario, solo no leídas y/o incluyendo vencidas. */
    @Query("""
            select n from Notification n
            where (:astronautId is null or n.astronautId = :astronautId)
              and (:unreadOnly = false or n.read = false)
              and (:includeExpired = true or n.expiresAt > :now)
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> search(@Param("astronautId") Long astronautId,
                              @Param("unreadOnly") boolean unreadOnly,
                              @Param("includeExpired") boolean includeExpired,
                              @Param("now") Instant now);

    @Query("""
            select count(n) from Notification n
            where (:astronautId is null or n.astronautId = :astronautId)
              and n.read = false and n.expiresAt > :now
            """)
    long countUnread(@Param("astronautId") Long astronautId, @Param("now") Instant now);

    @Modifying
    @Query("""
            update Notification n set n.read = true, n.readAt = :now
            where n.read = false and (:astronautId is null or n.astronautId = :astronautId)
            """)
    int markAllRead(@Param("astronautId") Long astronautId, @Param("now") Instant now);

    @Modifying
    @Query("delete from Notification n where n.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
