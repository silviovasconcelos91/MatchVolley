package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.dto.live.SetsWonDto;
import vasconcelos.volleymatch.dto.match.MatchDto;
import vasconcelos.volleymatch.mapper.LiveStatsMapper;
import vasconcelos.volleymatch.mapper.MatchMapper;
import vasconcelos.volleymatch.model.match.Match;
import vasconcelos.volleymatch.model.match.MatchResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchMappingTest {

    private final LiveStatsMapper liveStatsMapper = Mappers.getMapper(LiveStatsMapper.class);
    private final MatchMapper matchMapper = Mappers.getMapper(MatchMapper.class);

    private LiveStatsRequest request(String venue, int mine, int opp) {
        return new LiveStatsRequest("m-1", 42L, "Les Lynx", "VSL vs Grenoble", venue,
                Instant.parse("2026-06-05T14:45:00Z"), new SetsWonDto(mine, opp), List.of());
    }

    @Test
    void should_mapTitleAndDerivedFields_when_liveRequestMappedToMatch() {
        Match match = liveStatsMapper.toMatchEntity(request("home", 3, 1));

        assertThat(match.getId()).isEqualTo("m-1");
        assertThat(match.getTeamId()).isEqualTo(42L);
        assertThat(match.getTitle()).isEqualTo("VSL vs Grenoble");
        assertThat(match.getHome()).isTrue();
        assertThat(match.getDate()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(match.getMySets()).isEqualTo(3);
        assertThat(match.getOppSets()).isEqualTo(1);
        assertThat(match.getResult()).isEqualTo(MatchResult.WON);
    }

    @Test
    void should_deriveLostAndAway_when_oppWinsAwayMatch() {
        Match match = liveStatsMapper.toMatchEntity(request("away", 1, 3));

        assertThat(match.getHome()).isFalse();
        assertThat(match.getResult()).isEqualTo(MatchResult.LOST);
    }

    @Test
    void should_returnLowercaseResult_when_matchMappedToDto() {
        Match match = Match.builder().id("m-1").teamId(42L).title("VSL vs Grenoble").home(true)
                .date(LocalDate.of(2026, 6, 5)).result(MatchResult.WON).mySets(3).oppSets(1).build();

        MatchDto dto = matchMapper.toMatchDto(match);

        assertThat(dto.result()).isEqualTo("won");
        assertThat(dto.title()).isEqualTo("VSL vs Grenoble");
        assertThat(dto.home()).isTrue();
        assertThat(dto.mySets()).isEqualTo(3);
        assertThat(dto.oppSets()).isEqualTo(1);
    }
}
