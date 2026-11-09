package vasconcelos.volleymatch.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vasconcelos.volleymatch.model.live.LiveSession;
import vasconcelos.volleymatch.model.live.LiveSessionHeader;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LiveSessionRepository extends JpaRepository<LiveSession, UUID> {

    boolean existsByCode(String code);

    Optional<LiveSession> findByCodeAndExpiresAtAfter(String code, Instant now);

    @Query("""
            SELECT new vasconcelos.volleymatch.model.live.LiveSessionHeader(s.id, s.revision, s.status, s.showPlayerNames)
            FROM LiveSession s
            WHERE s.code = :code AND s.expiresAt > :now
            """)
    Optional<LiveSessionHeader> findHeaderByCode(@Param("code") String code, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM LiveSession s WHERE s.id = :id")
    Optional<LiveSession> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM LiveSession s WHERE s.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
