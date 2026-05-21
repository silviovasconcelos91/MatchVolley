package vasconcelos.silvio.volleymatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.NoSuchElementException;
import vasconcelos.silvio.volleymatch.dto.match.MatchDetailResponse;
import vasconcelos.silvio.volleymatch.dto.match.MatchStatRequest;
import vasconcelos.silvio.volleymatch.dto.match.MatchStatResponse;
import vasconcelos.silvio.volleymatch.mapper.MatchStatMapper;
import vasconcelos.silvio.volleymatch.model.match.Match;
import vasconcelos.silvio.volleymatch.model.user.AppUser;
import vasconcelos.silvio.volleymatch.repository.MatchRepository;
import vasconcelos.silvio.volleymatch.service.AuthService;
import vasconcelos.silvio.volleymatch.service.MatchStatService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchStatServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchStatMapper matchStatMapper;

    @Mock
    private AuthService authService;

    private AppUser testUser;

    @InjectMocks
    private MatchStatService matchStatService;

    @BeforeEach
    void setUp() {
        testUser = AppUser.builder()
                .email("test@test.com").pseudo("test").password("hashed").build();
        when(authService.getCurrentUser()).thenReturn(testUser);
    }

    @Test
    void should_returnMatchStatResponse_when_matchIsSaved() {
        MatchStatRequest request = mock(MatchStatRequest.class);
        Match match = Match.builder().id("match-unit-01").teamId(1L).date(LocalDate.now())
                .mySets(3).oppSets(1).result(vasconcelos.silvio.volleymatch.model.match.MatchResult.WON)
                .build();
        when(matchStatMapper.toEntity(request)).thenReturn(match);
        when(matchRepository.save(match)).thenReturn(match);

        MatchStatResponse response = matchStatService.saveMatchStat(request);

        assertThat(response.matchId()).isEqualTo("match-unit-01");
        verify(matchRepository).save(match);
    }

    @Test
    void should_returnMatchDetail_when_matchExists() {
        Match match = Match.builder().id("match-unit-02").teamId(1L).date(LocalDate.now())
                .mySets(3).oppSets(0).result(vasconcelos.silvio.volleymatch.model.match.MatchResult.WON)
                .build();
        MatchDetailResponse expected = new MatchDetailResponse(
                "match-unit-02", 1L, null, null, null, null, null, LocalDate.now(), "WON", 3, 0, null, List.of(), List.of());
        when(matchRepository.findByIdAndUser("match-unit-02", testUser)).thenReturn(Optional.of(match));
        when(matchStatMapper.toMatchDetailResponse(match)).thenReturn(expected);

        MatchDetailResponse result = matchStatService.getMatchStat("match-unit-02");

        assertThat(result.id()).isEqualTo("match-unit-02");
        assertThat(result.result()).isEqualTo("WON");
    }

    @Test
    void should_throwNoSuchElementException_when_matchNotFound() {
        when(matchRepository.findByIdAndUser("unknown", testUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchStatService.getMatchStat("unknown"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Match not found");
    }
}
