package vasconcelos.silvio.volleymatch.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vasconcelos.silvio.volleymatch.dto.team.CreateTeamRequest;
import vasconcelos.silvio.volleymatch.dto.team.TeamDto;
import vasconcelos.silvio.volleymatch.dto.team.TeamPlayerDto;
import vasconcelos.silvio.volleymatch.model.player.Player;
import vasconcelos.silvio.volleymatch.model.team.Team;

@Mapper(componentModel = "spring")
public interface TeamMapper {

    TeamDto toDto(Team team);

    TeamPlayerDto toPlayerDto(Player player);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "players", ignore = true)
    Team toEntity(CreateTeamRequest request);
}