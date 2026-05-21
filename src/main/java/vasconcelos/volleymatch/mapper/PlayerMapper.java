package vasconcelos.volleymatch.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vasconcelos.volleymatch.dto.player.CreatePlayerRequest;
import vasconcelos.volleymatch.dto.player.PlayerDto;
import vasconcelos.volleymatch.model.player.Player;
import vasconcelos.volleymatch.model.team.Team;

@Mapper(componentModel = "spring")
public interface PlayerMapper {

    @Mapping(target = "teamIds", source = "teams")
    PlayerDto toDto(Player player);

    default Long teamToId(Team team) {
        return team == null ? null : team.getId();
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "teams", ignore = true)
    Player toEntity(CreatePlayerRequest request);
}