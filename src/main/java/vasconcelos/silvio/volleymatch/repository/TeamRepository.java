package vasconcelos.silvio.volleymatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vasconcelos.silvio.volleymatch.model.team.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {
}
