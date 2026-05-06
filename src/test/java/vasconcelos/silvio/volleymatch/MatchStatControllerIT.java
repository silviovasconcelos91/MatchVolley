package vasconcelos.silvio.volleymatch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import vasconcelos.silvio.volleymatch.dto.common.ApiResponse;
import vasconcelos.silvio.volleymatch.dto.match.MatchDetailResponse;
import vasconcelos.silvio.volleymatch.dto.match.MatchDto;
import vasconcelos.silvio.volleymatch.dto.match.MatchStatRequest;
import vasconcelos.silvio.volleymatch.dto.match.MetaDto;
import vasconcelos.silvio.volleymatch.dto.match.PlayerSetStatDto;
import vasconcelos.silvio.volleymatch.dto.match.PlayerStatDto;
import vasconcelos.silvio.volleymatch.dto.match.SetStatDto;
import vasconcelos.silvio.volleymatch.dto.match.StatsDto;
import vasconcelos.silvio.volleymatch.dto.match.TimelineEntryDto;
import vasconcelos.silvio.volleymatch.model.match.Match;
import vasconcelos.silvio.volleymatch.repository.MatchRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MatchStatControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MatchRepository matchRepository;

    @Test
    void should_return201AndPersistMatch_when_validPayloadIsPosted() {
        MatchStatRequest request = buildValidRequest("match-001");

        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/v1/match-stats", request, ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(201);
        assertThat(matchRepository.findById("match-001")).isPresent();
    }

    @Test
    void should_persistSetsAndPlayers_when_validPayloadIsPosted() {
        MatchStatRequest request = buildValidRequest("match-002");
        restTemplate.postForEntity("/api/v1/match-stats", request, ApiResponse.class);

        Match saved = matchRepository.findById("match-002").orElseThrow();
        assertThat(saved.getSets()).hasSize(2);
        assertThat(saved.getPlayers()).hasSize(1);
    }

    @Test
    void should_persistTimelineAsJson_when_validPayloadIsPosted() {
        MatchStatRequest request = buildValidRequest("match-003");
        restTemplate.postForEntity("/api/v1/match-stats", request, ApiResponse.class);

        Match saved = matchRepository.findById("match-003").orElseThrow();
        assertThat(saved.getSets().get(0).getTimeline()).hasSize(2);
    }

    @Test
    void should_return200AndMatchDetail_when_matchExists() {
        restTemplate.postForEntity("/api/v1/match-stats", buildValidRequest("match-get-001"), ApiResponse.class);

        ResponseEntity<ApiResponse<MatchDetailResponse>> response = restTemplate.exchange(
                "/api/v1/match-stats/match-get-001",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        MatchDetailResponse data = response.getBody().data();
        assertThat(data.id()).isEqualTo("match-get-001");
        assertThat(data.result()).isEqualTo("WIN");
        assertThat(data.teamMatchStats()).isNotNull();
        assertThat(data.teamMatchStats().points()).isEqualTo(10);
        assertThat(data.sets()).hasSize(2);
        assertThat(data.players()).hasSize(1);
    }

    @Test
    void should_return404_when_matchDoesNotExist() {
        ResponseEntity<ApiResponse> response = restTemplate.getForEntity(
                "/api/v1/match-stats/unknown-match-id", ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_returnSetScoresAndPlayerSetStats_when_matchExists() {
        restTemplate.postForEntity("/api/v1/match-stats", buildValidRequest("match-get-002"), ApiResponse.class);

        ResponseEntity<ApiResponse<MatchDetailResponse>> response = restTemplate.exchange(
                "/api/v1/match-stats/match-get-002",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        MatchDetailResponse data = response.getBody().data();

        assertThat(data.sets().get(0).myScore()).isEqualTo(25);
        assertThat(data.sets().get(0).oppScore()).isEqualTo(20);
        assertThat(data.sets().get(0).teamStats()).isNotNull();

        PlayerStatDto player = data.players().get(0);
        assertThat(player.matchStats()).isNotNull();
        assertThat(player.setStats()).hasSize(1);
        assertThat(player.setStats().get(0).set()).isEqualTo(1);
        assertThat(player.setStats().get(0).points()).isEqualTo(5);
    }

    private MatchStatRequest buildValidRequest(String matchId) {
        StatsDto stats = new StatsDto(10, 5, 2, 3, 1, 2, 15);

        TimelineEntryDto event1 = new TimelineEntryDto(1, 0, 7L, "ace", Instant.now());
        TimelineEntryDto event2 = new TimelineEntryDto(1, 1, null, "error", Instant.now());

        SetStatDto set1 = new SetStatDto(1, 25, 20, stats, List.of(event1, event2));
        SetStatDto set2 = new SetStatDto(2, 25, 18, stats, List.of());

        PlayerSetStatDto playerSetStat = new PlayerSetStatDto(1, 5, 3, 1, 1, 0, 1, 8);
        PlayerStatDto player = new PlayerStatDto(42L, 7, "libero", stats, List.of(playerSetStat));

        MatchDto matchDto = new MatchDto(
                matchId, 1L, 2L, "2025-2026", 10L,
                LocalDate.of(2026, 3, 15), "win", 2, 0
        );

        MetaDto meta = new MetaDto(Instant.now(), "1.0.0");

        return new MatchStatRequest(matchDto, List.of(set1, set2), List.of(player), stats, meta);
    }
}
