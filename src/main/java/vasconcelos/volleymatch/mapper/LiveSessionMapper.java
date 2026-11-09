package vasconcelos.volleymatch.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vasconcelos.volleymatch.dto.live.LiveActionDto;
import vasconcelos.volleymatch.dto.live.LivePlayerDto;
import vasconcelos.volleymatch.dto.livesession.LiveMatchState;
import vasconcelos.volleymatch.dto.livesession.LiveSessionDto;
import vasconcelos.volleymatch.dto.livesession.LiveSnapshotResponse;
import vasconcelos.volleymatch.dto.livesession.PublicActionDto;
import vasconcelos.volleymatch.dto.livesession.PublicLiveViewDto;
import vasconcelos.volleymatch.dto.livesession.PublicPlayerDto;
import vasconcelos.volleymatch.model.live.LiveSession;

@Mapper(componentModel = "spring")
public interface LiveSessionMapper {

    @Mapping(target = "sessionId", source = "session.id")
    @Mapping(target = "publicUrl", source = "publicUrl")
    LiveSessionDto toDto(LiveSession session, String publicUrl);

    @Mapping(target = "revision", source = "session.revision")
    @Mapping(target = "applied", source = "applied")
    LiveSnapshotResponse toSnapshotResponse(LiveSession session, boolean applied);

    @Mapping(target = "code", source = "session.code")
    @Mapping(target = "title", source = "session.title")
    @Mapping(target = "teamName", source = "session.team.name")
    @Mapping(target = "venue", source = "session.venue")
    @Mapping(target = "status", source = "session.status")
    @Mapping(target = "updatedAt", source = "session.updatedAt")
    @Mapping(target = "setsWon", source = "state.setsWon")
    @Mapping(target = "currentSet", source = "state.currentSet")
    @Mapping(target = "sets", source = "state.sets")
    @Mapping(target = "lastPoints", source = "state.lastPoints")
    @Mapping(target = "topScorers", source = "state.topScorers")
    PublicLiveViewDto toPublicView(LiveSession session, LiveMatchState state);

    /**
     * Drops the internal player id; the name is only kept when {@code showName} is true.
     */
    @Mapping(target = "jersey", source = "player.jersey")
    @Mapping(target = "name", expression = "java(showName ? player.name() : null)")
    PublicPlayerDto toPublicPlayer(LivePlayerDto player, boolean showName);

    PublicActionDto toPublicAction(LiveActionDto action);
}
