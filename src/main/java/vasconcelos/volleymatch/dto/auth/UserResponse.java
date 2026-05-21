package vasconcelos.volleymatch.dto.auth;
import java.util.UUID;
public record UserResponse(UUID id, String email, String pseudo) {}
