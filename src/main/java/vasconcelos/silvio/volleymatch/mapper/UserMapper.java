package vasconcelos.silvio.volleymatch.mapper;

import org.mapstruct.Mapper;
import vasconcelos.silvio.volleymatch.dto.auth.UserResponse;
import vasconcelos.silvio.volleymatch.model.user.AppUser;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(AppUser user);
}
