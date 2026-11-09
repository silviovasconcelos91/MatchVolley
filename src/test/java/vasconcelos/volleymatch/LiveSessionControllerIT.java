package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import vasconcelos.volleymatch.dto.common.ApiResponse;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.dto.live.SetsWonDto;
import vasconcelos.volleymatch.dto.livesession.CreateLiveSessionRequest;
import vasconcelos.volleymatch.dto.livesession.LiveSessionDto;
import vasconcelos.volleymatch.dto.livesession.LiveSnapshotResponse;
import vasconcelos.volleymatch.dto.livesession.UpdateLiveSessionRequest;
import vasconcelos.volleymatch.model.live.LiveSession;
import vasconcelos.volleymatch.model.live.LiveSessionStatus;
import vasconcelos.volleymatch.repository.LiveSessionRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.set;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.simpleSnapshot;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.snapshot;

class LiveSessionControllerIT extends LiveSessionITSupport {

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_return201_with_code_and_public_url_when_team_is_owned() {
        Long teamId = createTeam("Les Lynx", authToken);

        ResponseEntity<ApiResponse<LiveSessionDto>> response = restTemplate.exchange(
                "/api/v1/live-sessions", HttpMethod.POST,
                authEntity(new CreateLiveSessionRequest(teamId, "Les Lynx vs Grenoble", "home", null)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        LiveSessionDto data = response.getBody().data();
        assertThat(data.sessionId()).isNotNull();
        assertThat(data.code()).matches("^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$");
        assertThat(data.publicUrl()).endsWith("/live/" + data.code());
        assertThat(data.status()).isEqualTo(LiveSessionStatus.LIVE);
        assertThat(data.showPlayerNames()).isFalse();
    }

    @Test
    void should_generate_unique_codes_when_creating_many_sessions() {
        Long teamId = createTeam("Unique codes", authToken);

        List<String> codes = IntStream.range(0, 20)
                .mapToObj(i -> createSession(teamId, false).code())
                .toList();

        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    void should_return403_when_team_belongs_to_other_user() {
        String otherToken = registerAndLogin("live_team_owner@test.com", "liveteamowner");
        Long foreignTeam = createTeam("Foreign team", otherToken);

        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/live-sessions", HttpMethod.POST,
                authEntity(new CreateLiveSessionRequest(foreignTeam, "Match", "home", false)),
                ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void should_return401_when_creating_without_token() {
        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/live-sessions", HttpMethod.POST,
                new HttpEntity<>(new CreateLiveSessionRequest(1L, "Match", "home", false)),
                ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_apply_newer_and_ignore_older_revision() {
        LiveSessionDto session = createSession(createTeam("Revisions", authToken), false);

        ResponseEntity<ApiResponse<LiveSnapshotResponse>> applied = putSnapshot(session, 17, simpleSnapshot(), authToken);
        ResponseEntity<ApiResponse<LiveSnapshotResponse>> stale = putSnapshot(session, 16, simpleSnapshot(), authToken);

        assertThat(applied.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(applied.getBody().data()).isEqualTo(new LiveSnapshotResponse(17, true));
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stale.getBody().data()).isEqualTo(new LiveSnapshotResponse(17, false));
        LiveSession stored = liveSessionRepository.findById(session.sessionId()).orElseThrow();
        assertThat(stored.getRevision()).isEqualTo(17);
        assertThat(stored.getSnapshot()).isEqualTo(simpleSnapshot());
    }

    @Test
    void should_store_snapshot_as_jsonb_object_when_applied() {
        LiveSessionDto session = createSession(createTeam("Jsonb", authToken), false);

        putSnapshot(session, 1, simpleSnapshot(), authToken);

        String jsonType = jdbcTemplate.queryForObject(
                "SELECT jsonb_typeof(snapshot) FROM live_sessions WHERE id = ?", String.class, session.sessionId());
        String teamName = jdbcTemplate.queryForObject(
                "SELECT snapshot ->> 'teamName' FROM live_sessions WHERE id = ?", String.class, session.sessionId());
        assertThat(jsonType).isEqualTo("object");
        assertThat(teamName).isEqualTo(simpleSnapshot().teamName());
    }

    @Test
    void should_return409_when_snapshot_sent_after_end() {
        LiveSessionDto session = createSession(createTeam("Ended", authToken), false);
        restTemplate.exchange("/api/v1/live-sessions/" + session.sessionId() + ":end",
                HttpMethod.POST, authEntity(), ApiResponse.class);

        ResponseEntity<ApiResponse<LiveSnapshotResponse>> response = putSnapshot(session, 1, simpleSnapshot(), authToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void should_return400_when_snapshot_payload_invalid() {
        LiveSessionDto session = createSession(createTeam("Invalid", authToken), false);

        ResponseEntity<ApiResponse<LiveSnapshotResponse>> response =
                putSnapshot(session, 1, snapshot(List.of(set(7, null, List.of()))), authToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_return400_when_poste_is_unknown() {
        LiveSessionDto session = createSession(createTeam("Bad poste", authToken), false);
        String body = """
                {"revision":1,"payload":{"sets":[{"setNumber":1,"events":[{"sequence":1,"team":"mine",
                "player":{"jersey":7,"poste":"Goalkeeper"},"action":{"key":"attack_pt","category":"point"},
                "scoredFor":"mine"}]}]}}""";
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/live-sessions/" + session.sessionId() + "/snapshot", HttpMethod.PUT,
                new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_return403_when_snapshot_sent_by_other_user() {
        LiveSessionDto session = createSession(createTeam("Mine only", authToken), false);
        String otherToken = registerAndLogin("live_intruder@test.com", "liveintruder");

        ResponseEntity<ApiResponse<LiveSnapshotResponse>> response = putSnapshot(session, 1, simpleSnapshot(), otherToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void should_return413_when_snapshot_body_exceeds_one_megabyte() {
        LiveSessionDto session = createSession(createTeam("Too large", authToken), false);
        String body = "{\"revision\":1,\"payload\":{\"title\":\"" + "x".repeat(1_100_000) + "\"}}";
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/live-sessions/" + session.sessionId() + "/snapshot", HttpMethod.PUT,
                new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    }

    @Test
    void should_end_session_idempotently() {
        LiveSessionDto session = createSession(createTeam("End twice", authToken), false);
        String url = "/api/v1/live-sessions/" + session.sessionId() + ":end";

        ResponseEntity<ApiResponse<LiveSessionDto>> first = restTemplate.exchange(url, HttpMethod.POST, authEntity(),
                new ParameterizedTypeReference<>() {});
        ResponseEntity<ApiResponse<LiveSessionDto>> second = restTemplate.exchange(url, HttpMethod.POST, authEntity(),
                new ParameterizedTypeReference<>() {});

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().data().status()).isEqualTo(LiveSessionStatus.ENDED);
        assertThat(second.getBody().data().endedAt()).isEqualTo(first.getBody().data().endedAt());
        assertThat(second.getBody().data().expiresAt()).isEqualTo(first.getBody().data().endedAt().plusSeconds(24 * 3600));
    }

    @Test
    void should_update_showPlayerNames_when_patched() {
        LiveSessionDto session = createSession(createTeam("Patch", authToken), false);

        ResponseEntity<ApiResponse<LiveSessionDto>> response = restTemplate.exchange(
                "/api/v1/live-sessions/" + session.sessionId(), HttpMethod.PATCH,
                authEntity(new UpdateLiveSessionRequest(true)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().showPlayerNames()).isTrue();
    }

    @Test
    void should_ignore_other_fields_when_patched() {
        LiveSessionDto session = createSession(createTeam("Patch others", authToken), false);

        ResponseEntity<ApiResponse<LiveSessionDto>> response = restTemplate.exchange(
                "/api/v1/live-sessions/" + session.sessionId(), HttpMethod.PATCH,
                authEntity(Map.of("showPlayerNames", true, "title", "Hacked", "status", "ENDED")),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getBody().data().title()).isEqualTo("Les Lynx vs Grenoble");
        assertThat(response.getBody().data().status()).isEqualTo(LiveSessionStatus.LIVE);
    }

    @Test
    void should_return404_when_session_unknown() {
        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/live-sessions/" + UUID.randomUUID() + ":end",
                HttpMethod.POST, authEntity(), ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_end_session_with_matchId_when_live_stats_posted_with_liveSessionId() {
        Long teamId = createTeam("Final submit", authToken);
        LiveSessionDto session = createSession(teamId, false);
        putSnapshot(session, 1, simpleSnapshot(), authToken);
        LiveStatsRequest finalStats = withLiveSession(simpleSnapshot(), "live-final-001", teamId, session.sessionId());

        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/matches/live-final-001/live-stats", HttpMethod.POST,
                authEntity(finalStats), ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        LiveSession stored = liveSessionRepository.findById(session.sessionId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(LiveSessionStatus.ENDED);
        assertThat(stored.getMatchId()).isEqualTo("live-final-001");
        assertThat(stored.getEndedAt()).isNotNull();
    }

    @Test
    void should_save_match_and_ignore_liveSessionId_when_session_belongs_to_other_user() {
        String otherToken = registerAndLogin("live_foreign_session@test.com", "liveforeign");
        Long otherTeam = createTeam("Other team", otherToken);
        ResponseEntity<ApiResponse<LiveSessionDto>> foreign = restTemplate.exchange(
                "/api/v1/live-sessions", HttpMethod.POST,
                new HttpEntity<>(new CreateLiveSessionRequest(otherTeam, "Other", "away", false), bearer(otherToken)),
                new ParameterizedTypeReference<>() {});
        UUID foreignId = foreign.getBody().data().sessionId();
        Long myTeam = createTeam("My team", authToken);

        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/matches/live-final-002/live-stats", HttpMethod.POST,
                authEntity(withLiveSession(simpleSnapshot(), "live-final-002", myTeam, foreignId)), ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        LiveSession stored = liveSessionRepository.findById(foreignId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(LiveSessionStatus.LIVE);
        assertThat(stored.getMatchId()).isNull();
    }

    private static LiveStatsRequest withLiveSession(LiveStatsRequest base, String matchId, Long teamId, UUID sessionId) {
        return new LiveStatsRequest(matchId, teamId, base.teamName(), base.title(), base.venue(),
                base.recordedAt(), new SetsWonDto(0, 0), base.sets(), sessionId);
    }
}
