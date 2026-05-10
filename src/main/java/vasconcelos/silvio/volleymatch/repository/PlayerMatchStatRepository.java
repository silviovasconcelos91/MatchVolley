package vasconcelos.silvio.volleymatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vasconcelos.silvio.volleymatch.model.match.PlayerMatchStat;

import java.util.List;

public interface PlayerMatchStatRepository extends JpaRepository<PlayerMatchStat, Long> {
    List<PlayerMatchStat> findByPlayerIdAndMatchTeamId(Long playerId, Long teamId);
}
