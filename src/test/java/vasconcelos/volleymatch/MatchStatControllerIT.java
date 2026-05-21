package vasconcelos.volleymatch;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vasconcelos.volleymatch.dto.common.ApiResponse;
import vasconcelos.volleymatch.dto.match.MatchDetailResponse;
import vasconcelos.volleymatch.dto.match.MatchDto;
import vasconcelos.volleymatch.dto.match.MatchStatRequest;
import vasconcelos.volleymatch.dto.match.MetaDto;
import vasconcelos.volleymatch.dto.match.PlayerSetStatDto;
import vasconcelos.volleymatch.dto.match.PlayerStatDto;
import vasconcelos.volleymatch.dto.match.SetStatDto;
import vasconcelos.volleymatch.dto.match.StatsDto;
import vasconcelos.volleymatch.dto.match.TimelineEntryDto;
import vasconcelos.volleymatch.model.match.Match;
import vasconcelos.volleymatch.model.match.VolleyPosition;
import vasconcelos.volleymatch.repository.MatchRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchStatControllerIT extends BaseIT {

    @Autowired
    private MatchRepository matchRepository;

    @Test
    void should_return201AndPersistMatch_when_validPayloadIsPosted() {
        MatchStatRequest request = buildValidRequest("match-001");

        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/match-stats", HttpMethod.POST, authEntity(request), ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(201);
        assertThat(matchRepository.findById("match-001")).isPresent();
    }

    @Test
    @Transactional
    void should_persistSetsAndPlayers_when_validPayloadIsPosted() {
        MatchStatRequest request = buildValidRequest("match-002");
        restTemplate.exchange("/api/v1/match-stats", HttpMethod.POST, authEntity(request), ApiResponse.class);

        Match saved = matchRepository.findById("match-002").orElseThrow();
        assertThat(saved.getSets()).hasSize(2);
        assertThat(saved.getPlayers()).hasSize(1);
    }

    @Test
    @Transactional
    void should_persistTimelineAsJson_when_validPayloadIsPosted() {
        MatchStatRequest request = buildValidRequest("match-003");
        restTemplate.exchange("/api/v1/match-stats", HttpMethod.POST, authEntity(request), ApiResponse.class);

        Match saved = matchRepository.findById("match-003").orElseThrow();
        assertThat(saved.getSets().getFirst().getTimeline()).hasSize(2);
    }

    @Test
    void should_return200AndMatchDetail_when_matchExists() {
        restTemplate.exchange("/api/v1/match-stats", HttpMethod.POST, authEntity(buildValidRequest("match-get-001")), ApiResponse.class);

        ResponseEntity<ApiResponse<MatchDetailResponse>> response = restTemplate.exchange(
                "/api/v1/match-stats/match-get-001",
                HttpMethod.GET,
                authEntity(),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        MatchDetailResponse data = response.getBody().data();
        assertThat(data.id()).isEqualTo("match-get-001");
        assertThat(data.result()).isEqualTo("WON");
        assertThat(data.teamMatchStats()).isNotNull();
        assertThat(data.teamMatchStats().points()).isEqualTo(10);
        assertThat(data.sets()).hasSize(2);
        assertThat(data.players()).hasSize(1);
    }

    @Test
    void should_return404_when_matchDoesNotExist() {
        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/match-stats/unknown-match-id", HttpMethod.GET, authEntity(), ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_returnSetScoresAndPlayerSetStats_when_matchExists() {
        restTemplate.exchange("/api/v1/match-stats", HttpMethod.POST, authEntity(buildValidRequest("match-get-002")), ApiResponse.class);

        ResponseEntity<ApiResponse<MatchDetailResponse>> response = restTemplate.exchange(
                "/api/v1/match-stats/match-get-002",
                HttpMethod.GET,
                authEntity(),
                new ParameterizedTypeReference<>() {});

        MatchDetailResponse data = response.getBody().data();

        assertThat(data.sets().getFirst().myScore()).isEqualTo(25);
        assertThat(data.sets().getFirst().oppScore()).isEqualTo(20);
        assertThat(data.sets().getFirst().teamStats()).isNotNull();

        PlayerStatDto player = data.players().getFirst();
        assertThat(player.matchStats()).isNotNull();
        assertThat(player.setStats()).hasSize(1);
        assertThat(player.setStats().getFirst().set()).isEqualTo(1);
        assertThat(player.setStats().getFirst().points()).isEqualTo(5);
    }

    private MatchStatRequest buildValidRequest(String matchId) {
        StatsDto stats = new StatsDto(10, 5, 2, 3, 1, 2, 15, 4);

        TimelineEntryDto event1 = new TimelineEntryDto(1, 0, 7L, "ace", Instant.now());
        TimelineEntryDto event2 = new TimelineEntryDto(1, 1, null, "error", Instant.now());

        SetStatDto set1 = new SetStatDto(1, 25, 20, stats, List.of(event1, event2));
        SetStatDto set2 = new SetStatDto(2, 25, 18, stats, List.of());

        PlayerSetStatDto playerSetStat = new PlayerSetStatDto(1, "Libero", 5, 3, 1, 1, 0, 1, 8, 2);
        PlayerStatDto player = new PlayerStatDto(42L, 7, VolleyPosition.Libero, stats, List.of(playerSetStat));

        MatchDto matchDto = new MatchDto(
                matchId, 1L, 2L, "2025-2026", 10L,
                "Finale Coupe", true,
                LocalDate.of(2026, 3, 15), "WON", 2, 0
        );

        MetaDto meta = new MetaDto(Instant.now(), "1.0.0");

        return new MatchStatRequest(matchDto, List.of(set1, set2), List.of(player), stats, meta);
    }
}
