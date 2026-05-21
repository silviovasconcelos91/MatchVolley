package vasconcelos.volleymatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vasconcelos.volleymatch.model.player.Player;
import vasconcelos.volleymatch.model.user.AppUser;

import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    List<Player> findAllByUser(AppUser user);
    Optional<Player> findByIdAndUser(Long id, AppUser user);
    boolean existsByIdAndUser(Long id, AppUser user);

    @Query("SELECT p FROM Player p WHERE p.id IN :ids AND p.user = :user")
    List<Player> findAllByIdInAndUser(@Param("ids") List<Long> ids, @Param("user") AppUser user);
}
