package vasconcelos.silvio.volleymatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vasconcelos.silvio.volleymatch.model.match.PlayerMatchStat;

import java.util.List;

public interface PlayerMatchStatRepository extends JpaRepository<PlayerMatchStat, Long> {
    List<PlayerMatchStat> findByMatchId(String matchId);
    List<PlayerMatchStat> findByPlayerId(Long playerId);
    List<PlayerMatchStat> findByPlayerIdAndMatchTeamId(Long playerId, Long teamId);
}
