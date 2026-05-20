package vasconcelos.silvio.volleymatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vasconcelos.silvio.volleymatch.model.match.Match;
import vasconcelos.silvio.volleymatch.model.user.AppUser;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, String> {
    List<Match> findByTeamId(Long teamId);
    List<Match> findByTeamIdAndUser(Long teamId, AppUser user);
    Optional<Match> findByIdAndUser(String id, AppUser user);
}
