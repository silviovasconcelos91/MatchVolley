package vasconcelos.volleymatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.model.user.EmailVerificationToken;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByToken(UUID token);
    Optional<EmailVerificationToken> findByUser(AppUser user);
}