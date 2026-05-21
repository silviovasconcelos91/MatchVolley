package vasconcelos.volleymatch.mapper;

import org.mapstruct.Mapper;
import vasconcelos.volleymatch.dto.auth.UserResponse;
import vasconcelos.volleymatch.model.user.AppUser;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(AppUser user);
}
