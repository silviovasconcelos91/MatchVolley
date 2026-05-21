package vasconcelos.silvio.volleymatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vasconcelos.silvio.volleymatch.model.team.Team;
import vasconcelos.silvio.volleymatch.model.user.AppUser;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findAllByUser(AppUser user);
    Optional<Team> findByIdAndUser(Long id, AppUser user);
    boolean existsByIdAndUser(Long id, AppUser user);

    @Query("SELECT t FROM Team t WHERE t.id IN :ids AND t.user = :user")
    List<Team> findAllByIdInAndUser(@Param("ids") List<Long> ids, @Param("user") AppUser user);
}
