package vasconcelos.volleymatch.dto.livesession;

import java.util.List;

/**
 * @param serving    team that won the last rally ("mine" / "opp"), null before the first point
 * @param trajectory cumulative score after each point, starting with {0, 0}
 */
public record PublicCurrentSetDto(
        int setNumber,
        ScoreDto score,
        String serving,
        List<ScoreDto> trajectory
) {}
