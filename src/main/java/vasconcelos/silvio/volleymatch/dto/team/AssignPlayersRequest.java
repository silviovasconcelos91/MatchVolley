package vasconcelos.silvio.volleymatch.dto.team;

import java.util.List;

public record AssignPlayersRequest(
        List<Long> playerIds
) {
}
