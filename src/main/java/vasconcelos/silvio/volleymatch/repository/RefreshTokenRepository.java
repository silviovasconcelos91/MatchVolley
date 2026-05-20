package vasconcelos.silvio.volleymatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vasconcelos.silvio.volleymatch.model.user.AppUser;
import vasconcelos.silvio.volleymatch.model.user.RefreshToken;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);
    void deleteAllByUser(AppUser user);
}
