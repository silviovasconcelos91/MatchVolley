package vasconcelos.volleymatch.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vasconcelos.volleymatch.dto.match.MatchDetailResponse;
import vasconcelos.volleymatch.dto.match.MatchDto;
import vasconcelos.volleymatch.dto.match.MatchStatRequest;
import vasconcelos.volleymatch.dto.match.PlayerSetStatDto;
import vasconcelos.volleymatch.dto.match.PlayerStatDto;
import vasconcelos.volleymatch.dto.match.SetStatDto;
import vasconcelos.volleymatch.dto.match.StatsDto;
import vasconcelos.volleymatch.model.match.Match;
import vasconcelos.volleymatch.model.match.MatchResult;
import vasconcelos.volleymatch.model.match.PlayerMatchStat;
import vasconcelos.volleymatch.model.match.PlayerSetStat;
import vasconcelos.volleymatch.model.match.SetStat;
import vasconcelos.volleymatch.model.match.VolleyPosition;
import vasconcelos.volleymatch.model.match.VolleyStats;

@Mapper(componentModel = "spring")
public interface MatchStatMapper {

    @Mapping(target = "id", source = "match.id")
    @Mapping(target = "teamId", source = "match.teamId")
    @Mapping(target = "opponentId", source = "match.opponentId")
    @Mapping(target = "seasonId", source = "match.seasonId")
    @Mapping(target = "competitionId", source = "match.competitionId")
    @Mapping(target = "title", source = "match.title")
    @Mapping(target = "home", source = "match.home")
    @Mapping(target = "date", source = "match.date")
    @Mapping(target = "result", source = "match.result")
    @Mapping(target = "mySets", source = "match.mySets")
    @Mapping(target = "oppSets", source = "match.oppSets")
    Match toEntity(MatchStatRequest request);

    VolleyStats toVolleyStats(StatsDto dto);

    StatsDto toStatsDto(VolleyStats stats);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "match", ignore = true)
    SetStat toSetStatEntity(SetStatDto dto);

    @Mapping(target = "teamStats", source = "teamStats")
    SetStatDto toSetStatDto(SetStat setStat);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "match", ignore = true)
    @Mapping(target = "matchStats", source = "matchStats")
    @Mapping(target = "setStats", source = "setStats")
    PlayerMatchStat toPlayerMatchStatEntity(PlayerStatDto dto);

    @Mapping(target = "matchStats", source = "matchStats")
    PlayerStatDto toPlayerStatDto(PlayerMatchStat playerMatchStat);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "set", source = "set")
    @Mapping(target = "position", expression = "java(toVolleyPosition(dto.position()))")
    @Mapping(target = "playerMatchStat", ignore = true)
    @Mapping(target = "stats.points", source = "points")
    @Mapping(target = "stats.attackPoints", source = "attackPoints")
    @Mapping(target = "stats.blockPoints", source = "blockPoints")
    @Mapping(target = "stats.acePoints", source = "acePoints")
    @Mapping(target = "stats.attackErrors", source = "attackErrors")
    @Mapping(target = "stats.serviceErrors", source = "serviceErrors")
    @Mapping(target = "stats.receptions", source = "receptions")
    @Mapping(target = "stats.faults", source = "faults")
    PlayerSetStat toPlayerSetStatEntity(PlayerSetStatDto dto);

    @Mapping(target = "position", expression = "java(playerSetStat.getPosition() != null ? playerSetStat.getPosition().name() : null)")
    @Mapping(target = "points", source = "stats.points")
    @Mapping(target = "attackPoints", source = "stats.attackPoints")
    @Mapping(target = "blockPoints", source = "stats.blockPoints")
    @Mapping(target = "acePoints", source = "stats.acePoints")
    @Mapping(target = "attackErrors", source = "stats.attackErrors")
    @Mapping(target = "serviceErrors", source = "stats.serviceErrors")
    @Mapping(target = "receptions", source = "stats.receptions")
    @Mapping(target = "faults", source = "stats.faults")
    PlayerSetStatDto toPlayerSetStatDto(PlayerSetStat playerSetStat);

    @Mapping(target = "result", expression = "java(match.getResult() != null ? match.getResult().name() : null)")
    MatchDto toMatchDto(Match match);

    @Mapping(target = "result", expression = "java(match.getResult() != null ? match.getResult().name() : null)")
    @Mapping(target = "teamMatchStats", source = "teamMatchStats")
    @Mapping(target = "sets", source = "sets")
    @Mapping(target = "players", source = "players")
    MatchDetailResponse toMatchDetailResponse(Match match);

    default MatchResult toMatchResult(String result) {
        if (result == null) return null;
        return MatchResult.valueOf(result.toUpperCase());
    }

    default VolleyPosition toVolleyPosition(String position) {
        if (position == null) return null;
        for (VolleyPosition p : VolleyPosition.values()) {
            if (p.name().equalsIgnoreCase(position)) return p;
        }
        throw new IllegalArgumentException("Unknown position: " + position);
    }

    @AfterMapping
    default void wireBackReferences(@MappingTarget Match match) {
        if (match.getSets() != null) {
            match.getSets().forEach(s -> s.setMatch(match));
        }
        if (match.getPlayers() != null) {
            match.getPlayers().forEach(p -> {
                p.setMatch(match);
                if (p.getSetStats() != null) {
                    p.getSetStats().forEach(ps -> ps.setPlayerMatchStat(p));
                }
            });
        }
    }
}
