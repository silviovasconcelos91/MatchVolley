package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vasconcelos.volleymatch.dto.common.ApiResponse;
import vasconcelos.volleymatch.dto.live.LiveActionDto;
import vasconcelos.volleymatch.dto.live.LiveEventDto;
import vasconcelos.volleymatch.dto.live.LivePlayerDto;
import vasconcelos.volleymatch.dto.live.LiveSetDto;
import vasconcelos.volleymatch.dto.live.LiveStatsSavedResponse;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.dto.live.LiveStatsResponse;
import vasconcelos.volleymatch.dto.live.LiveTrajectoryDto;
import vasconcelos.volleymatch.dto.live.SetsWonDto;
import vasconcelos.volleymatch.repository.MatchRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LiveStatsControllerIT extends BaseIT {

    @Autowired
    private MatchRepository matchRepository;

    @Test
    void should_return201_and_persist_match_when_valid_payload_is_posted() {
        LiveStatsRequest request = buildValidRequest("live-match-001");

        ResponseEntity<ApiResponse<LiveStatsSavedResponse>> response = restTemplate.exchange(
                "/api/v1/matches/live-match-001/live-stats",
                HttpMethod.POST,
                authEntity(request),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().matchId()).isEqualTo("live-match-001");
        assertThat(matchRepository.findById("live-match-001")).isPresent();
    }

    @Test
    void should_return409_when_match_id_already_exists() {
        LiveStatsRequest request = buildValidRequest("live-match-dup");
        restTemplate.exchange("/api/v1/matches/live-match-dup/live-stats",
                HttpMethod.POST, authEntity(request), ApiResponse.class);

        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/matches/live-match-dup/live-stats",
                HttpMethod.POST,
                authEntity(buildValidRequest("live-match-dup")),
                ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void should_return200_and_full_payload_when_match_exists() {
        LiveStatsRequest request = buildValidRequest("live-match-get-001");
        restTemplate.exchange("/api/v1/matches/live-match-get-001/live-stats",
                HttpMethod.POST, authEntity(request), ApiResponse.class);

        ResponseEntity<ApiResponse<LiveStatsResponse>> response = restTemplate.exchange(
                "/api/v1/matches/live-match-get-001/live-stats",
                HttpMethod.GET,
                authEntity(),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LiveStatsResponse data = response.getBody().data();
        assertThat(data.matchId()).isEqualTo("live-match-get-001");
        assertThat(data.teamName()).isEqualTo("Les Lynx");
        assertThat(data.venue()).isEqualTo("home");
        assertThat(data.setsWon().mine()).isEqualTo(2);
        assertThat(data.setsWon().opp()).isEqualTo(1);
        assertThat(data.sets()).hasSize(1);
        assertThat(data.sets().getFirst().events()).hasSize(1);
    }

    @Test
    void should_derive_result_WIN_when_sets_won_mine_greater() {
        LiveStatsRequest request = buildValidRequest("live-match-win");
        restTemplate.exchange("/api/v1/matches/live-match-win/live-stats",
                HttpMethod.POST, authEntity(request), ApiResponse.class);

        assertThat(matchRepository.findById("live-match-win"))
                .isPresent()
                .get()
                .extracting(m -> m.getResult().name())
                .isEqualTo("WON");
    }

    @Test
    void should_return404_when_match_does_not_exist() {
        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/matches/nonexistent-live/live-stats",
                HttpMethod.GET,
                authEntity(),
                ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_return401_when_not_authenticated() {
        ResponseEntity<ApiResponse> postResponse = restTemplate.exchange(
                "/api/v1/matches/live-unauth/live-stats",
                HttpMethod.POST,
                new HttpEntity<>(buildValidRequest("live-unauth")),
                ApiResponse.class);

        ResponseEntity<ApiResponse> getResponse = restTemplate.exchange(
                "/api/v1/matches/live-unauth/live-stats",
                HttpMethod.GET,
                new HttpEntity<>((Void) null),
                ApiResponse.class);

        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private LiveStatsRequest buildValidRequest(String matchId) {
        LiveEventDto event = new LiveEventDto(
                "ev-001", 1, 1749131100000L, "mine",
                new LivePlayerDto(3L, 7, "Dupont", 4),
                new LiveActionDto("attack_pt", "Attaque", "point"),
                new LiveTrajectoryDto(4, 1),
                "mine"
        );
        LiveSetDto set = new LiveSetDto(1, 4, 2, "mine", List.of(event));
        return new LiveStatsRequest(
                matchId, 42L, "Les Lynx", "home",
                Instant.parse("2026-06-05T14:45:00.000Z"),
                new SetsWonDto(2, 1),
                List.of(set)
        );
    }
}
