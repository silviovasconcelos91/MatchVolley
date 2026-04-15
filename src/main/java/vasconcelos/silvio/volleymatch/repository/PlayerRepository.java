package vasconcelos.silvio.volleymatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vasconcelos.silvio.volleymatch.model.player.Player;

public interface PlayerRepository extends JpaRepository<Player, Long> {
}
