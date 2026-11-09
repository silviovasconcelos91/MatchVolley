package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vasconcelos.volleymatch.dto.common.ApiResponse;
import vasconcelos.volleymatch.dto.livesession.LiveSessionDto;
import vasconcelos.volleymatch.dto.livesession.PublicLiveViewDto;
import vasconcelos.volleymatch.dto.livesession.ScoreDto;
import vasconcelos.volleymatch.dto.livesession.UpdateLiveSessionRequest;
import vasconcelos.volleymatch.model.live.LiveSession;
import vasconcelos.volleymatch.model.live.LiveSessionStatus;
import vasconcelos.volleymatch.repository.LiveSessionRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.attackPoint;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.opponentPoint;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.set;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.simpleSnapshot;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.snapshot;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.timeout;

class PublicLiveControllerIT extends LiveSessionITSupport {

    private static final String PUBLIC_URL = "/api/v1/public/live/";
    private static final AtomicInteger IP_COUNTER = new AtomicInteger();

    @Autowired
    private LiveSessionRepository liveSessionRepository;

    private HttpHeaders anonymous() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "203.0.113." + (IP_COUNTER.incrementAndGet() % 250));
        return headers;
    }

    private ResponseEntity<ApiResponse<PublicLiveViewDto>> getPublic(String code, HttpHeaders headers) {
        return restTemplate.exchange(PUBLIC_URL + code, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});
    }

    private LiveSessionDto liveSessionWithSnapshot(boolean showNames) {
        LiveSessionDto session = createSession(createTeam("Les Lynx", authToken), showNames);
        putSnapshot(session, 1, simpleSnapshot(), authToken);
        return session;
    }

    @Test
    void should_show_timeout_banner_and_counter_until_next_event() {
        LiveSessionDto session = createSession(createTeam("Timeouts", authToken), false);
        putSnapshot(session, 1, snapshot(List.of(set(1, null, List.of(
                attackPoint(1, 3L, 7, "Dupont"),
                timeout(2, "mine"))))), authToken);

        PublicLiveViewDto during = getPublic(session.code(), anonymous()).getBody().data();

        putSnapshot(session, 2, snapshot(List.of(set(1, null, List.of(
                attackPoint(1, 3L, 7, "Dupont"),
                timeout(2, "mine"),
                opponentPoint(3, 12))))), authToken);

        PublicLiveViewDto after = getPublic(session.code(), anonymous()).getBody().data();

        assertThat(during.currentSet().activeTimeout()).isEqualTo("mine");
        assertThat(during.currentSet().timeouts()).isEqualTo(new ScoreDto(1, 0));
        assertThat(after.currentSet().activeTimeout()).isNull();
        assertThat(after.currentSet().timeouts()).isEqualTo(new ScoreDto(1, 0));
        assertThat(after.currentSet().score()).isEqualTo(new ScoreDto(1, 1));
    }

    @Test
    void should_return200_without_token_with_computed_view() {
        LiveSessionDto session = liveSessionWithSnapshot(false);

        ResponseEntity<ApiResponse<PublicLiveViewDto>> response = getPublic(session.code(), anonymous());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isNotBlank();
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-cache");
        PublicLiveViewDto view = response.getBody().data();
        assertThat(view.code()).isEqualTo(session.code());
        assertThat(view.title()).isEqualTo("Les Lynx vs Grenoble");
        assertThat(view.teamName()).isEqualTo("Les Lynx");
        assertThat(view.venue()).isEqualTo("home");
        assertThat(view.status()).isEqualTo(LiveSessionStatus.LIVE);
        assertThat(view.currentSet().score()).isEqualTo(new ScoreDto(2, 1));
        assertThat(view.currentSet().serving()).isEqualTo("mine");
        assertThat(view.lastPoints()).hasSize(3);
        assertThat(view.topScorers()).singleElement().satisfies(t -> {
            assertThat(t.jersey()).isEqualTo(7);
            assertThat(t.points()).isEqualTo(1);
        });
    }

    @Test
    void should_accept_lowercase_code() {
        LiveSessionDto session = liveSessionWithSnapshot(false);

        ResponseEntity<ApiResponse<PublicLiveViewDto>> response = getPublic(session.code().toLowerCase(), anonymous());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_return404_when_code_unknown() {
        assertThat(getPublic("ZZZZZZ", anonymous()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getPublic("bad", anonymous()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_return_same404_when_session_expired() {
        LiveSessionDto session = liveSessionWithSnapshot(false);
        LiveSession stored = liveSessionRepository.findById(session.sessionId()).orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(60));
        liveSessionRepository.save(stored);

        ResponseEntity<String> expired = restTemplate.exchange(PUBLIC_URL + session.code(), HttpMethod.GET,
                new HttpEntity<>(anonymous()), String.class);
        ResponseEntity<String> unknown = restTemplate.exchange(PUBLIC_URL + "ZZZZZY", HttpMethod.GET,
                new HttpEntity<>(anonymous()), String.class);

        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(expired.getBody()).isEqualTo(unknown.getBody());
    }

    @Test
    void should_return304_without_body_when_etag_matches() {
        LiveSessionDto session = liveSessionWithSnapshot(false);
        String etag = getPublic(session.code(), anonymous()).getHeaders().getETag();

        HttpHeaders headers = anonymous();
        headers.setIfNoneMatch(etag);
        ResponseEntity<String> response = restTemplate.exchange(PUBLIC_URL + session.code(), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders().getETag()).isEqualTo(etag);
    }

    @Test
    void should_return200_with_new_etag_when_snapshot_changes() {
        LiveSessionDto session = liveSessionWithSnapshot(false);
        String etag = getPublic(session.code(), anonymous()).getHeaders().getETag();
        putSnapshot(session, 2, snapshot(List.of(set(1, null, List.of(attackPoint(1, 3L, 7, "Dupont"))))), authToken);

        HttpHeaders headers = anonymous();
        headers.setIfNoneMatch(etag);
        ResponseEntity<ApiResponse<PublicLiveViewDto>> response = getPublic(session.code(), headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isNotEqualTo(etag);
        assertThat(response.getBody().data().currentSet().score()).isEqualTo(new ScoreDto(1, 0));
    }

    @Test
    void should_change_etag_when_showPlayerNames_toggled() {
        LiveSessionDto session = liveSessionWithSnapshot(false);
        String etag = getPublic(session.code(), anonymous()).getHeaders().getETag();
        restTemplate.exchange("/api/v1/live-sessions/" + session.sessionId(), HttpMethod.PATCH,
                authEntity(new UpdateLiveSessionRequest(true)), ApiResponse.class);

        HttpHeaders headers = anonymous();
        headers.setIfNoneMatch(etag);
        ResponseEntity<ApiResponse<PublicLiveViewDto>> response = getPublic(session.code(), headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().topScorers().getFirst().name()).isEqualTo("Dupont");
    }

    @Test
    void should_hide_names_when_showPlayerNames_false() {
        LiveSessionDto session = liveSessionWithSnapshot(false);

        PublicLiveViewDto view = getPublic(session.code(), anonymous()).getBody().data();

        assertThat(view.lastPoints()).allSatisfy(p -> assertThat(p.player().name()).isNull());
        assertThat(view.topScorers()).allSatisfy(t -> assertThat(t.name()).isNull());
    }

    @Test
    void should_show_only_my_names_when_showPlayerNames_true() {
        LiveSessionDto session = liveSessionWithSnapshot(true);

        PublicLiveViewDto view = getPublic(session.code(), anonymous()).getBody().data();

        assertThat(view.lastPoints()).extracting(p -> p.player().name()).containsExactly(null, null, "Dupont");
    }

    @Test
    void should_never_expose_sensitive_fields() {
        LiveSessionDto session = liveSessionWithSnapshot(true);

        ResponseEntity<String> response = restTemplate.exchange(PUBLIC_URL + session.code(), HttpMethod.GET,
                new HttpEntity<>(anonymous()), String.class);

        assertThat(response.getBody())
                .doesNotContain(session.sessionId().toString())
                .doesNotContain("sessionId")
                .doesNotContain("ownerUserId")
                .doesNotContain("owner")
                .doesNotContain("teamId")
                .doesNotContain("\"id\"")
                .doesNotContain("matchId");
        assertThat(response.getHeaders().getETag()).doesNotContain(session.sessionId().toString());
    }

    @Test
    void should_lower_public_score_when_undo_snapshot_has_fewer_events() {
        LiveSessionDto session = liveSessionWithSnapshot(false);
        putSnapshot(session, 2, snapshot(List.of(set(1, null, List.of(
                attackPoint(1, 3L, 7, "Dupont"), opponentPoint(2, 12))))), authToken);

        PublicLiveViewDto view = getPublic(session.code(), anonymous()).getBody().data();

        assertThat(view.currentSet().score()).isEqualTo(new ScoreDto(1, 1));
        assertThat(view.currentSet().serving()).isEqualTo("opp");
    }

    @Test
    void should_keep_showing_last_snapshot_when_session_ended() {
        LiveSessionDto session = liveSessionWithSnapshot(false);
        restTemplate.exchange("/api/v1/live-sessions/" + session.sessionId() + ":end",
                HttpMethod.POST, authEntity(), ApiResponse.class);

        PublicLiveViewDto view = getPublic(session.code(), anonymous()).getBody().data();

        assertThat(view.status()).isEqualTo(LiveSessionStatus.ENDED);
        assertThat(view.currentSet().score()).isEqualTo(new ScoreDto(2, 1));
    }

    @Test
    void should_return_empty_view_when_no_snapshot_yet() {
        LiveSessionDto session = createSession(createTeam("Empty", authToken), false);

        ResponseEntity<ApiResponse<PublicLiveViewDto>> response = getPublic(session.code(), anonymous());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().currentSet()).isNull();
        assertThat(response.getBody().data().sets()).isEmpty();
    }

    @Test
    void should_allow_cors_and_expose_etag_for_front_origin() {
        LiveSessionDto session = liveSessionWithSnapshot(false);
        HttpHeaders preflightHeaders = new HttpHeaders();
        preflightHeaders.setOrigin("http://localhost:8081");
        preflightHeaders.setAccessControlRequestMethod(HttpMethod.GET);
        preflightHeaders.setAccessControlRequestHeaders(List.of("If-None-Match"));
        HttpHeaders getHeaders = anonymous();
        getHeaders.setOrigin("http://localhost:8081");

        ResponseEntity<String> preflight = restTemplate.exchange(PUBLIC_URL + session.code(), HttpMethod.OPTIONS,
                new HttpEntity<>(preflightHeaders), String.class);
        ResponseEntity<String> get = restTemplate.exchange(PUBLIC_URL + session.code(), HttpMethod.GET,
                new HttpEntity<>(getHeaders), String.class);

        assertThat(preflight.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(preflight.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:8081");
        assertThat(preflight.getHeaders().getAccessControlAllowHeaders())
                .map(String::toLowerCase).contains("if-none-match");
        assertThat(get.getHeaders().getAccessControlExposeHeaders()).map(String::toLowerCase).contains("etag");
    }

    @Test
    void should_not_open_other_routes_without_token() {
        ResponseEntity<String> response = restTemplate.exchange("/api/v1/live-sessions/" + UUID.randomUUID() + ":end",
                HttpMethod.POST, new HttpEntity<>(anonymous()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
