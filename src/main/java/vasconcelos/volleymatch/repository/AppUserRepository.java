package vasconcelos.volleymatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vasconcelos.volleymatch.model.user.AppUser;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByPseudo(String pseudo);
}
