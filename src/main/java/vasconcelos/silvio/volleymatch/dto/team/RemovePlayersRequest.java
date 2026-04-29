package vasconcelos.silvio.volleymatch.dto.team;

import java.util.List;

public record RemovePlayersRequest(
        List<Long> playerIds
) {
}
