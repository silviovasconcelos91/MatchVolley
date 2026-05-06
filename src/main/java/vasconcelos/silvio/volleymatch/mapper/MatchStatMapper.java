package vasconcelos.silvio.volleymatch.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vasconcelos.silvio.volleymatch.dto.match.MatchDetailResponse;
import vasconcelos.silvio.volleymatch.dto.match.MatchStatRequest;
import vasconcelos.silvio.volleymatch.dto.match.PlayerSetStatDto;
import vasconcelos.silvio.volleymatch.dto.match.PlayerStatDto;
import vasconcelos.silvio.volleymatch.dto.match.SetStatDto;
import vasconcelos.silvio.volleymatch.dto.match.StatsDto;
import vasconcelos.silvio.volleymatch.model.match.Match;
import vasconcelos.silvio.volleymatch.model.match.MatchResult;
import vasconcelos.silvio.volleymatch.model.match.PlayerMatchStat;
import vasconcelos.silvio.volleymatch.model.match.PlayerSetStat;
import vasconcelos.silvio.volleymatch.model.match.SetStat;
import vasconcelos.silvio.volleymatch.model.match.VolleyStats;

@Mapper(componentModel = "spring")
public interface MatchStatMapper {

    @Mapping(target = "id", source = "match.id")
    @Mapping(target = "teamId", source = "match.teamId")
    @Mapping(target = "opponentId", source = "match.opponentId")
    @Mapping(target = "seasonId", source = "match.seasonId")
    @Mapping(target = "competitionId", source = "match.competitionId")
    @Mapping(target = "date", source = "match.date")
    @Mapping(target = "result", source = "match.result")
    @Mapping(target = "mySets", source = "match.mySets")
    @Mapping(target = "oppSets", source = "match.oppSets")
    @Mapping(target = "teamMatchStats", source = "teamMatchStats")
    @Mapping(target = "sets", source = "sets")
    @Mapping(target = "players", source = "players")
    Match toEntity(MatchStatRequest request);

    VolleyStats toVolleyStats(StatsDto dto);

    StatsDto toStatsDto(VolleyStats stats);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "match", ignore = true)
    @Mapping(target = "teamStats", source = "teamStats")
    @Mapping(target = "timeline", source = "timeline")
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
    @Mapping(target = "playerMatchStat", ignore = true)
    @Mapping(target = "stats.points", source = "points")
    @Mapping(target = "stats.attackPoints", source = "attackPoints")
    @Mapping(target = "stats.blockPoints", source = "blockPoints")
    @Mapping(target = "stats.acePoints", source = "acePoints")
    @Mapping(target = "stats.attackErrors", source = "attackErrors")
    @Mapping(target = "stats.serviceErrors", source = "serviceErrors")
    @Mapping(target = "stats.receptions", source = "receptions")
    PlayerSetStat toPlayerSetStatEntity(PlayerSetStatDto dto);

    @Mapping(target = "points", source = "stats.points")
    @Mapping(target = "attackPoints", source = "stats.attackPoints")
    @Mapping(target = "blockPoints", source = "stats.blockPoints")
    @Mapping(target = "acePoints", source = "stats.acePoints")
    @Mapping(target = "attackErrors", source = "stats.attackErrors")
    @Mapping(target = "serviceErrors", source = "stats.serviceErrors")
    @Mapping(target = "receptions", source = "stats.receptions")
    PlayerSetStatDto toPlayerSetStatDto(PlayerSetStat playerSetStat);

    @Mapping(target = "result", expression = "java(match.getResult() != null ? match.getResult().name() : null)")
    @Mapping(target = "teamMatchStats", source = "teamMatchStats")
    @Mapping(target = "sets", source = "sets")
    @Mapping(target = "players", source = "players")
    MatchDetailResponse toMatchDetailResponse(Match match);

    default MatchResult toMatchResult(String result) {
        if (result == null) return null;
        return MatchResult.valueOf(result.toUpperCase());
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
