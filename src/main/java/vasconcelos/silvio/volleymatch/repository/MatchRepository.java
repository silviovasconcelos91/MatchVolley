package vasconcelos.silvio.volleymatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vasconcelos.silvio.volleymatch.model.match.Match;

public interface MatchRepository extends JpaRepository<Match, String> {
}
