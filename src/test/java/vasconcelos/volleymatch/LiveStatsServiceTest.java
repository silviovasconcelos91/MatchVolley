package vasconcelos.volleymatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vasconcelos.volleymatch.dto.live.LiveSetDto;
import vasconcelos.volleymatch.dto.live.LiveStatsSavedResponse;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.dto.live.LiveStatsResponse;
import vasconcelos.volleymatch.dto.live.SetsWonDto;
import vasconcelos.volleymatch.mapper.LiveStatsMapper;
import vasconcelos.volleymatch.model.match.Match;
import vasconcelos.volleymatch.model.match.MatchLiveSession;
import vasconcelos.volleymatch.model.match.MatchResult;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.repository.MatchLiveSessionRepository;
import vasconcelos.volleymatch.repository.MatchRepository;
import vasconcelos.volleymatch.service.AuthService;
import vasconcelos.volleymatch.service.LiveStatsService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LiveStatsServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchLiveSessionRepository liveSessionRepository;

    @Mock
    private LiveStatsMapper liveStatsMapper;

    @Mock
    private AuthService authService;

    @InjectMocks
    private LiveStatsService liveStatsService;

    private AppUser testUser;

    @BeforeEach
    void setUp() {
        testUser = AppUser.builder()
                .email("test@test.com").pseudo("test").password("hashed").build();
        when(authService.getCurrentUser()).thenReturn(testUser);
    }

    @Test
    void should_create_match_and_session_when_payload_is_valid() {
        LiveStatsRequest request = buildRequest("match-live-01", 2, 1);
        Match match = buildMatch("match-live-01", 2, 1, MatchResult.WON);
        MatchLiveSession session = MatchLiveSession.builder()
                .teamName("Les Lynx").recordedAt(Instant.now())
                .setsWonMine(2).setsWonOpp(1).build();

        when(matchRepository.existsById("match-live-01")).thenReturn(false);
        when(liveStatsMapper.toMatchEntity(request)).thenReturn(match);
        when(matchRepository.save(match)).thenReturn(match);
        when(liveStatsMapper.toSessionEntity(request)).thenReturn(session);
        when(liveSessionRepository.save(session)).thenReturn(session);

        LiveStatsSavedResponse response = liveStatsService.saveLiveStats(request);

        assertThat(response.matchId()).isEqualTo("match-live-01");
        verify(matchRepository).save(match);
        verify(liveSessionRepository).save(session);
    }

    @Test
    void should_throw_409_when_match_id_already_exists() {
        LiveStatsRequest request = buildRequest("match-dup", 2, 1);
        when(matchRepository.existsById("match-dup")).thenReturn(true);

        assertThatThrownBy(() -> liveStatsService.saveLiveStats(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("match-dup");
    }

    @Test
    void should_return_reconstructed_payload_when_match_exists() {
        LiveStatsResponse expected = new LiveStatsResponse(
                "match-live-02", 42L, "Les Lynx", "home",
                Instant.now(), new SetsWonDto(2, 0), List.of());
        MatchLiveSession session = MatchLiveSession.builder()
                .teamName("Les Lynx").recordedAt(Instant.now())
                .setsWonMine(2).setsWonOpp(0).build();

        when(liveSessionRepository.findByMatch_IdAndMatch_User("match-live-02", testUser))
                .thenReturn(Optional.of(session));
        when(liveStatsMapper.toResponse(session)).thenReturn(expected);

        LiveStatsResponse result = liveStatsService.getLiveStats("match-live-02");

        assertThat(result.matchId()).isEqualTo("match-live-02");
        assertThat(result.teamName()).isEqualTo("Les Lynx");
    }

    @Test
    void should_throw_404_when_match_not_found() {
        when(liveSessionRepository.findByMatch_IdAndMatch_User("unknown", testUser))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> liveStatsService.getLiveStats("unknown"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("unknown");
    }

    private LiveStatsRequest buildRequest(String matchId, int mine, int opp) {
        return new LiveStatsRequest(
                matchId, 42L, "Les Lynx", "home",
                Instant.now(), new SetsWonDto(mine, opp), List.of());
    }

    private Match buildMatch(String id, int mySets, int oppSets, MatchResult result) {
        return Match.builder()
                .id(id).teamId(42L).home(true)
                .date(LocalDate.now()).mySets(mySets).oppSets(oppSets).result(result)
                .build();
    }
}
