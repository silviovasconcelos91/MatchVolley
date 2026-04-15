package vasconcelos.silvio.volleymatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vasconcelos.silvio.volleymatch.model.match.SetStat;

import java.util.List;

public interface SetStatRepository extends JpaRepository<SetStat, Long> {
    List<SetStat> findByMatchId(String matchId);
}
