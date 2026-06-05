package vasconcelos.volleymatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vasconcelos.volleymatch.model.match.MatchLiveSession;
import vasconcelos.volleymatch.model.user.AppUser;

import java.util.Optional;

public interface MatchLiveSessionRepository extends JpaRepository<MatchLiveSession, Long> {
    Optional<MatchLiveSession> findByMatch_IdAndMatch_User(String matchId, AppUser user);
}
