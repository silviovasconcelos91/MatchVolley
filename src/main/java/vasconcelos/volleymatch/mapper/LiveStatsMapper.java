package vasconcelos.volleymatch.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vasconcelos.volleymatch.dto.live.LiveSetDto;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.dto.live.LiveStatsResponse;
import vasconcelos.volleymatch.dto.live.SetsWonDto;
import vasconcelos.volleymatch.model.match.Match;
import vasconcelos.volleymatch.model.match.MatchLiveSession;
import vasconcelos.volleymatch.model.match.MatchLiveSetEvent;
import vasconcelos.volleymatch.model.match.MatchResult;

import java.time.ZoneOffset;

@Mapper(componentModel = "spring", imports = {MatchResult.class, ZoneOffset.class, SetsWonDto.class})
public interface LiveStatsMapper {

    @Mapping(target = "id", source = "matchId")
    @Mapping(target = "home", expression = "java(\"home\".equals(request.venue()))")
    @Mapping(target = "date", expression = "java(request.recordedAt().atZone(ZoneOffset.UTC).toLocalDate())")
    @Mapping(target = "mySets", source = "setsWon.mine")
    @Mapping(target = "oppSets", source = "setsWon.opp")
    @Mapping(target = "result", expression = "java(request.setsWon().mine() > request.setsWon().opp() ? MatchResult.WON : MatchResult.LOST)")
    @Mapping(target = "opponentId", ignore = true)
    @Mapping(target = "seasonId", ignore = true)
    @Mapping(target = "competitionId", ignore = true)
    @Mapping(target = "title", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "teamMatchStats", ignore = true)
    @Mapping(target = "sets", ignore = true)
    @Mapping(target = "players", ignore = true)
    Match toMatchEntity(LiveStatsRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "match", ignore = true)
    @Mapping(target = "setsWonMine", source = "setsWon.mine")
    @Mapping(target = "setsWonOpp", source = "setsWon.opp")
    @Mapping(target = "sets", source = "sets")
    MatchLiveSession toSessionEntity(LiveStatsRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "session", ignore = true)
    @Mapping(target = "set", source = "setNumber")
    MatchLiveSetEvent toSetEventEntity(LiveSetDto dto);

    @Mapping(target = "matchId", source = "match.id")
    @Mapping(target = "teamId", source = "match.teamId")
    @Mapping(target = "venue", expression = "java(Boolean.TRUE.equals(session.getMatch().getHome()) ? \"home\" : \"away\")")
    @Mapping(target = "setsWon", expression = "java(new SetsWonDto(session.getSetsWonMine(), session.getSetsWonOpp()))")
    @Mapping(target = "sets", source = "sets")
    LiveStatsResponse toResponse(MatchLiveSession session);

    @Mapping(target = "setNumber", source = "set")
    LiveSetDto toSetDto(MatchLiveSetEvent setEvent);

    @AfterMapping
    default void wireSetBackReferences(@MappingTarget MatchLiveSession session) {
        if (session.getSets() != null) {
            session.getSets().forEach(s -> s.setSession(session));
        }
    }
}
