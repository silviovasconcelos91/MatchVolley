package vasconcelos.volleymatch.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vasconcelos.volleymatch.dto.team.CreateTeamRequest;
import vasconcelos.volleymatch.dto.team.TeamDto;
import vasconcelos.volleymatch.dto.team.TeamPlayerDto;
import vasconcelos.volleymatch.model.player.Player;
import vasconcelos.volleymatch.model.team.Team;

@Mapper(componentModel = "spring")
public interface TeamMapper {

    TeamDto toDto(Team team);

    TeamPlayerDto toPlayerDto(Player player);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "players", ignore = true)
    Team toEntity(CreateTeamRequest request);
}