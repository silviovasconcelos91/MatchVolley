package vasconcelos.volleymatch.dto.team;

public record CreateTeamRequest(
        String name,
        String city,
        String logoColor
) {}