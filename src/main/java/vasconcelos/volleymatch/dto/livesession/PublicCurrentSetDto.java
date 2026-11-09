package vasconcelos.volleymatch.dto.livesession;

import java.util.List;

/**
 * @param serving    team that won the last rally ("mine" / "opp"), null before the first point
 * @param trajectory    cumulative score after each point, starting with {0, 0}
 * @param timeouts      number of timeouts called by each team in this set
 * @param activeTimeout team whose timeout is in progress (last event of the set is a timeout), else null
 */
public record PublicCurrentSetDto(
        int setNumber,
        ScoreDto score,
        String serving,
        List<ScoreDto> trajectory,
        ScoreDto timeouts,
        String activeTimeout
) {}
