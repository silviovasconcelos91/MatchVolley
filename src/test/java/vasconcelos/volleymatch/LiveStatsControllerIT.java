package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import vasconcelos.volleymatch.dto.common.ApiResponse;
import vasconcelos.volleymatch.dto.live.LiveActionDto;
import vasconcelos.volleymatch.dto.live.LiveEventDto;
import vasconcelos.volleymatch.dto.live.LiveMatchAnalysisResponse;
import vasconcelos.volleymatch.dto.live.LivePlayerDto;
import vasconcelos.volleymatch.dto.live.LiveScoreDto;
import vasconcelos.volleymatch.dto.live.LiveSetAnalysisDto;
import vasconcelos.volleymatch.dto.live.LiveSetDto;
import vasconcelos.volleymatch.dto.live.LiveTimeoutDto;
import vasconcelos.volleymatch.dto.live.LiveStatsSavedResponse;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.dto.live.LiveStatsResponse;
import vasconcelos.volleymatch.dto.live.LiveTrajectoryDto;
import vasconcelos.volleymatch.dto.live.SetsWonDto;
import vasconcelos.volleymatch.dto.match.MatchDto;
import vasconcelos.volleymatch.dto.team.CreateTeamRequest;
import vasconcelos.volleymatch.dto.team.TeamDto;
import vasconcelos.volleymatch.model.match.VolleyPosition;
import vasconcelos.volleymatch.repository.MatchRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
        assertThat(data.title()).isEqualTo("VSL vs Grenoble");
        assertThat(data.sets().getFirst().events().getFirst().player().poste()).isEqualTo(VolleyPosition.R4);
    }

    @Test
    void should_listLiveMatchWithTitle_when_getTeamMatches() {
        ResponseEntity<ApiResponse<TeamDto>> team = restTemplate.exchange(
                "/api/v1/teams", HttpMethod.POST,
                authEntity(new CreateTeamRequest("Team live", "Lyon", "#000000")),
                new ParameterizedTypeReference<>() {});
        Long teamId = team.getBody().data().id();
        restTemplate.exchange("/api/v1/matches/live-match-team-01/live-stats",
                HttpMethod.POST, authEntity(buildValidRequest("live-match-team-01", teamId)), ApiResponse.class);

        ResponseEntity<ApiResponse<List<MatchDto>>> response = restTemplate.exchange(
                "/api/v1/teams/" + teamId + "/matches", HttpMethod.GET, authEntity(),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).singleElement().satisfies(m -> {
            assertThat(m.id()).isEqualTo("live-match-team-01");
            assertThat(m.title()).isEqualTo("VSL vs Grenoble");
            assertThat(m.home()).isTrue();
            assertThat(m.date()).isEqualTo(LocalDate.of(2026, 6, 5));
            assertThat(m.result()).isEqualTo("won");
            assertThat(m.mySets()).isEqualTo(2);
            assertThat(m.oppSets()).isEqualTo(1);
        });
    }

    @Test
    void should_return404_when_analysisRequestedForMatchWithoutLiveStats() {
        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/matches/no-live-stats/live-stats/analysis",
                HttpMethod.GET, authEntity(), ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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

    @Test
    void should_return_timeout_markers_in_analysis_when_match_has_timeouts() {
        LiveSetDto set = LiveSnapshotFixtures.set(1, null, List.of(
                LiveSnapshotFixtures.attackPoint(1, 3L, 7, "Dupont"),
                LiveSnapshotFixtures.opponentPoint(2, 12),
                LiveSnapshotFixtures.timeout(3, "opp"),
                LiveSnapshotFixtures.attackPoint(4, 3L, 7, "Dupont")));
        LiveStatsRequest request = new LiveStatsRequest("live-match-timeout", 42L, "Les Lynx", "VSL vs Grenoble",
                "home", Instant.parse("2026-06-05T14:45:00.000Z"), new SetsWonDto(0, 0), List.of(set));
        restTemplate.exchange("/api/v1/matches/live-match-timeout/live-stats",
                HttpMethod.POST, authEntity(request), ApiResponse.class);

        ResponseEntity<ApiResponse<LiveMatchAnalysisResponse>> response = restTemplate.exchange(
                "/api/v1/matches/live-match-timeout/live-stats/analysis",
                HttpMethod.GET, authEntity(), new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LiveSetAnalysisDto analysedSet = response.getBody().data().sets().get(0);
        assertThat(analysedSet.timeline()).hasSize(3);
        assertThat(analysedSet.timeouts()).containsExactly(
                new LiveTimeoutDto("opp", 2, 1, 1, Instant.ofEpochMilli(1_003L)));
        assertThat(response.getBody().data().globalStats().actions().neutral()).isEmpty();
    }

    // ── malus ─────────────────────────────────────────────────────────────────

    /** Set 1: malus -2 mine, then 27 points mine and 15 points opp; payload score 25-15 (malus already deducted). */
    private LiveStatsRequest malusRequest(String matchId) {
        List<LiveEventDto> events = new ArrayList<>();
        events.add(LiveSnapshotFixtures.malus(1, "mine", 2));
        int seq = 2;
        for (int i = 0; i < 27; i++) {
            events.add(LiveSnapshotFixtures.attackPoint(seq++, 3L, 7, "Dupont"));
        }
        for (int i = 0; i < 15; i++) {
            events.add(LiveSnapshotFixtures.opponentPoint(seq++, 12));
        }
        LiveSetDto set = new LiveSetDto(1, 25, 15, "mine", events);
        return new LiveStatsRequest(matchId, 42L, "Les Lynx", "VSL vs Grenoble", "home",
                Instant.parse("2026-06-05T14:45:00.000Z"), new SetsWonDto(1, 0), List.of(set));
    }

    @Test
    void should_return201_and_keep_payload_set_score_when_set_has_malus() {
        ResponseEntity<ApiResponse<LiveStatsSavedResponse>> post = restTemplate.exchange(
                "/api/v1/matches/live-match-malus/live-stats",
                HttpMethod.POST, authEntity(malusRequest("live-match-malus")), new ParameterizedTypeReference<>() {});

        ResponseEntity<ApiResponse<LiveStatsResponse>> get = restTemplate.exchange(
                "/api/v1/matches/live-match-malus/live-stats",
                HttpMethod.GET, authEntity(), new ParameterizedTypeReference<>() {});
        ResponseEntity<ApiResponse<LiveMatchAnalysisResponse>> analysis = restTemplate.exchange(
                "/api/v1/matches/live-match-malus/live-stats/analysis",
                HttpMethod.GET, authEntity(), new ParameterizedTypeReference<>() {});

        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        LiveSetDto storedSet = get.getBody().data().sets().getFirst();
        assertThat(storedSet.scoreTeam()).isEqualTo(25);
        assertThat(storedSet.scoreOpp()).isEqualTo(15);
        assertThat(storedSet.events().getFirst().malusAmount()).isEqualTo(2);
        LiveSetAnalysisDto analysedSet = analysis.getBody().data().sets().getFirst();
        assertThat(analysedSet.myScore()).isEqualTo(25);
        assertThat(analysedSet.oppScore()).isEqualTo(15);
        assertThat(analysedSet.startScore()).isEqualTo(new LiveScoreDto(-2, 0));
        assertThat(analysedSet.timeline()).hasSize(42);
        assertThat(analysedSet.timeline().getFirst().myScore()).isEqualTo(-1);
        assertThat(analysedSet.timeline().getLast().myScore()).isEqualTo(25);
        assertThat(analysedSet.timeline().getLast().oppScore()).isEqualTo(15);
    }

    /** POSTs a one-set payload and checks the match was not created when rejected. */
    private ResponseEntity<ApiResponse> postOneSet(String matchId, List<LiveEventDto> events) {
        LiveStatsRequest request = new LiveStatsRequest(matchId, 42L, "Les Lynx", "VSL vs Grenoble", "home",
                Instant.parse("2026-06-05T14:45:00.000Z"), new SetsWonDto(0, 0),
                List.of(LiveSnapshotFixtures.set(1, null, events)));
        return restTemplate.exchange("/api/v1/matches/" + matchId + "/live-stats",
                HttpMethod.POST, authEntity(request), ApiResponse.class);
    }

    private void assertRejected(ResponseEntity<ApiResponse> response, String matchId, String messagePart) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).contains(messagePart);
        assertThat(matchRepository.findById(matchId)).isEmpty();
    }

    @Test
    void should_return400_when_posted_malus_amount_is_invalid() {
        assertRejected(postOneSet("post-malus-3", List.of(LiveSnapshotFixtures.malus(1, "mine", 3))),
                "post-malus-3", "malusAmount");
        assertRejected(postOneSet("post-malus-0", List.of(LiveSnapshotFixtures.malus(1, "mine", 0))),
                "post-malus-0", "malusAmount");
        assertRejected(postOneSet("post-malus-null", List.of(LiveSnapshotFixtures.malus(1, "mine", null))),
                "post-malus-null", "malusAmount");
    }

    @Test
    void should_return400_when_posted_non_malus_event_has_malus_amount() {
        LiveEventDto point = LiveSnapshotFixtures.attackPoint(1, 3L, 7, "Dupont");
        LiveEventDto pointWithAmount = new LiveEventDto(point.id(), point.sequence(), point.ts(), point.team(),
                point.player(), point.action(), point.trajectory(), point.scoredFor(), 1);

        assertRejected(postOneSet("post-amount-on-point", List.of(pointWithAmount)),
                "post-amount-on-point", "malusAmount");
    }

    @Test
    void should_return400_when_posted_malus_key_and_category_mismatch() {
        LiveEventDto keyOnly = new LiveEventDto("e1", 1, 1L, "mine", null,
                new LiveActionDto("malus", "Malus", "neutral"), null, null, 2);
        LiveEventDto categoryOnly = new LiveEventDto("e1", 1, 1L, "mine",
                new LivePlayerDto(3L, 7, "Dupont", 4, null),
                new LiveActionDto("attack_pt", "Attaque", "malus"), null, null, null);

        assertRejected(postOneSet("post-key-only", List.of(keyOnly)), "post-key-only", "requires action.category 'malus'");
        assertRejected(postOneSet("post-cat-only", List.of(categoryOnly)), "post-cat-only", "requires action.key 'malus'");
    }

    @Test
    void should_return400_when_posted_malus_has_player() {
        LiveEventDto withPlayer = new LiveEventDto("e1", 1, 1L, "mine",
                new LivePlayerDto(3L, 7, "Dupont", 4, null),
                new LiveActionDto("malus", "Malus", "malus"), null, null, 2);

        assertRejected(postOneSet("post-malus-player", List.of(withPlayer)), "post-malus-player", "player null");
    }

    @Test
    void should_return400_when_posted_malus_comes_after_first_point() {
        assertRejected(postOneSet("post-late-malus", List.of(
                        LiveSnapshotFixtures.opponentPoint(1, 12),
                        LiveSnapshotFixtures.malus(2, "mine", 2))),
                "post-late-malus", "before the first point");
    }

    @Test
    void should_return400_when_posted_set_has_two_malus_for_same_team() {
        assertRejected(postOneSet("post-double-malus", List.of(
                        LiveSnapshotFixtures.malus(1, "mine", 1),
                        LiveSnapshotFixtures.malus(2, "mine", 1))),
                "post-double-malus", "at most one malus");
    }

    @Test
    void should_return201_when_posted_match_has_six_sets_after_a_tied_set() {
        LiveStatsRequest request = new LiveStatsRequest("post-six-sets", 42L, "Les Lynx", "Tournoi au temps", "home",
                Instant.parse("2026-06-05T14:45:00.000Z"), new SetsWonDto(2, 2), LiveSnapshotFixtures.sixSetsAfterTiedSet());

        ResponseEntity<ApiResponse> post = restTemplate.exchange("/api/v1/matches/post-six-sets/live-stats",
                HttpMethod.POST, authEntity(request), ApiResponse.class);
        ResponseEntity<ApiResponse<LiveMatchAnalysisResponse>> analysis = restTemplate.exchange(
                "/api/v1/matches/post-six-sets/live-stats/analysis",
                HttpMethod.GET, authEntity(), new ParameterizedTypeReference<>() {});

        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        List<LiveSetAnalysisDto> sets = analysis.getBody().data().sets();
        assertThat(sets).extracting(LiveSetAnalysisDto::setNumber).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);
        LiveSetAnalysisDto sixth = sets.stream().filter(s -> s.setNumber() == 6).findFirst().orElseThrow();
        assertThat(sixth.startScore()).isEqualTo(new LiveScoreDto(0, -1));
        assertThat(sixth.timeline()).hasSize(1);
    }

    @Test
    void should_return400_when_posted_match_has_more_than_ten_sets() {
        LiveStatsRequest request = new LiveStatsRequest("post-eleven-sets", 42L, "Les Lynx", "Trop de sets", "home",
                Instant.parse("2026-06-05T14:45:00.000Z"), new SetsWonDto(0, 0), LiveSnapshotFixtures.numberedSets(11));

        assertRejected(restTemplate.exchange("/api/v1/matches/post-eleven-sets/live-stats",
                HttpMethod.POST, authEntity(request), ApiResponse.class), "post-eleven-sets", "at most 10 sets");
    }

    @Test
    void should_return201_when_posted_set_has_one_malus_per_team() {
        ResponseEntity<ApiResponse> response = postOneSet("post-two-teams-malus", List.of(
                LiveSnapshotFixtures.malus(1, "mine", 2),
                LiveSnapshotFixtures.malus(2, "opp", 1),
                LiveSnapshotFixtures.opponentPoint(3, 12)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void should_return201_and_null_malus_amount_when_payload_has_no_malus_field() {
        String body = """
                {"matchId":"live-match-legacy","teamId":42,"teamName":"Les Lynx","title":"Legacy","venue":"home",
                "recordedAt":"2026-06-05T14:45:00.000Z","setsWon":{"mine":1,"opp":0},
                "sets":[{"setNumber":1,"scoreTeam":1,"scoreOpp":0,"wonBy":"mine","events":[
                {"id":"e1","sequence":1,"ts":1,"team":"mine","player":{"id":3,"jersey":7,"name":"Dupont"},
                "action":{"key":"attack_pt","label":"Attaque","category":"point"},"trajectory":null,"scoredFor":"mine"}]}]}""";
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> post = restTemplate.exchange("/api/v1/matches/live-match-legacy/live-stats",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        ResponseEntity<ApiResponse<LiveStatsResponse>> get = restTemplate.exchange(
                "/api/v1/matches/live-match-legacy/live-stats",
                HttpMethod.GET, authEntity(), new ParameterizedTypeReference<>() {});

        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(get.getBody().data().sets().getFirst().events().getFirst().malusAmount()).isNull();
    }

    private LiveStatsRequest buildValidRequest(String matchId) {
        return buildValidRequest(matchId, 42L);
    }

    private LiveStatsRequest buildValidRequest(String matchId, Long teamId) {
        LiveEventDto event = new LiveEventDto(
                "ev-001", 1, 1749131100000L, "mine",
                new LivePlayerDto(3L, 7, "Dupont", 4, VolleyPosition.R4),
                new LiveActionDto("attack_pt", "Attaque", "point"),
                new LiveTrajectoryDto(4, 1),
                "mine", null
        );
        LiveSetDto set = new LiveSetDto(1, 4, 2, "mine", List.of(event));
        return new LiveStatsRequest(
                matchId, teamId, "Les Lynx", "VSL vs Grenoble", "home",
                Instant.parse("2026-06-05T14:45:00.000Z"),
                new SetsWonDto(2, 1),
                List.of(set)
        );
    }
}
