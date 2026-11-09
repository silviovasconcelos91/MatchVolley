package vasconcelos.volleymatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;
import vasconcelos.volleymatch.dto.livesession.CreateLiveSessionRequest;
import vasconcelos.volleymatch.dto.livesession.LiveSessionDto;
import vasconcelos.volleymatch.dto.livesession.LiveSnapshotRequest;
import vasconcelos.volleymatch.dto.livesession.LiveSnapshotResponse;
import vasconcelos.volleymatch.dto.livesession.UpdateLiveSessionRequest;
import vasconcelos.volleymatch.exception.BadRequestException;
import vasconcelos.volleymatch.exception.ForbiddenException;
import vasconcelos.volleymatch.exception.LiveSessionEndedException;
import vasconcelos.volleymatch.mapper.LiveSessionMapper;
import vasconcelos.volleymatch.model.live.LiveSession;
import vasconcelos.volleymatch.model.live.LiveSessionStatus;
import vasconcelos.volleymatch.model.team.Team;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.repository.LiveSessionRepository;
import vasconcelos.volleymatch.repository.TeamRepository;
import vasconcelos.volleymatch.service.AuthService;
import vasconcelos.volleymatch.service.LiveSessionCodeGenerator;
import vasconcelos.volleymatch.service.LiveSessionService;
import vasconcelos.volleymatch.service.LiveStatsPayloadValidator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.set;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.simpleSnapshot;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.snapshot;

class LiveSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T19:00:00Z");

    private final LiveSessionRepository repository = mock(LiveSessionRepository.class);
    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final LiveSessionCodeGenerator codeGenerator = mock(LiveSessionCodeGenerator.class);
    private final AuthService authService = mock(AuthService.class);
    private final LiveStatsPayloadValidator validator = new LiveStatsPayloadValidator();

    private LiveSessionService service;
    private AppUser owner;
    private AppUser other;

    @BeforeEach
    void setUp() {
        service = new LiveSessionService(repository, teamRepository, Mappers.getMapper(LiveSessionMapper.class),
                codeGenerator, validator, authService, Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "publicWebUrl", "https://live.example.com/");
        owner = AppUser.builder().id(UUID.randomUUID()).email("owner@test.com").build();
        other = AppUser.builder().id(UUID.randomUUID()).email("other@test.com").build();
        when(authService.getCurrentUser()).thenReturn(owner);
        when(repository.save(any(LiveSession.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private LiveSession session(AppUser sessionOwner, LiveSessionStatus status, long revision) {
        return LiveSession.builder()
                .id(UUID.randomUUID())
                .code("K7P4QX")
                .owner(sessionOwner)
                .team(Team.builder().id(42L).name("Les Lynx").build())
                .title("Les Lynx vs Grenoble")
                .venue("home")
                .status(status)
                .revision(revision)
                .createdAt(NOW.minusSeconds(60))
                .updatedAt(NOW.minusSeconds(60))
                .expiresAt(NOW.plus(Duration.ofHours(1)))
                .build();
    }

    @Test
    void should_create_live_session_with_code_and_public_url_when_team_is_owned() {
        when(teamRepository.findByIdAndUser(42L, owner)).thenReturn(Optional.of(Team.builder().id(42L).name("Les Lynx").build()));
        when(codeGenerator.generate()).thenReturn("K7P4QX");

        LiveSessionDto dto = service.create(new CreateLiveSessionRequest(42L, "Les Lynx vs Grenoble", "home", null));

        assertThat(dto.code()).isEqualTo("K7P4QX");
        assertThat(dto.publicUrl()).isEqualTo("https://live.example.com/live/K7P4QX");
        assertThat(dto.status()).isEqualTo(LiveSessionStatus.LIVE);
        assertThat(dto.showPlayerNames()).isFalse();
        assertThat(dto.revision()).isZero();
        assertThat(dto.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
    }

    @Test
    void should_retry_code_generation_when_code_collides() {
        when(teamRepository.findByIdAndUser(42L, owner)).thenReturn(Optional.of(Team.builder().id(42L).name("Les Lynx").build()));
        when(codeGenerator.generate()).thenReturn("AAAAAA", "BBBBBB");
        when(repository.existsByCode("AAAAAA")).thenReturn(true);
        when(repository.existsByCode("BBBBBB")).thenReturn(false);

        LiveSessionDto dto = service.create(new CreateLiveSessionRequest(42L, "Match", "away", true));

        assertThat(dto.code()).isEqualTo("BBBBBB");
        assertThat(dto.showPlayerNames()).isTrue();
    }

    @Test
    void should_throw_forbidden_when_team_not_owned() {
        when(teamRepository.findByIdAndUser(99L, owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateLiveSessionRequest(99L, "Match", "home", false)))
                .isInstanceOf(ForbiddenException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void should_throw_bad_request_when_venue_invalid() {
        assertThatThrownBy(() -> service.create(new CreateLiveSessionRequest(42L, "Match", "neutral", false)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void should_apply_snapshot_and_extend_expiry_when_revision_is_newer() {
        LiveSession s = session(owner, LiveSessionStatus.LIVE, 3);
        when(repository.findByIdForUpdate(s.getId())).thenReturn(Optional.of(s));

        LiveSnapshotResponse response = service.updateSnapshot(s.getId(), new LiveSnapshotRequest(4L, simpleSnapshot()));

        assertThat(response.applied()).isTrue();
        assertThat(response.revision()).isEqualTo(4);
        assertThat(s.getSnapshot()).isEqualTo(simpleSnapshot());
        assertThat(s.getUpdatedAt()).isEqualTo(NOW);
        assertThat(s.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
    }

    @Test
    void should_ignore_snapshot_when_revision_is_not_newer() {
        LiveSession s = session(owner, LiveSessionStatus.LIVE, 17);
        when(repository.findByIdForUpdate(s.getId())).thenReturn(Optional.of(s));

        LiveSnapshotResponse older = service.updateSnapshot(s.getId(), new LiveSnapshotRequest(16L, simpleSnapshot()));
        LiveSnapshotResponse same = service.updateSnapshot(s.getId(), new LiveSnapshotRequest(17L, simpleSnapshot()));

        assertThat(older.applied()).isFalse();
        assertThat(older.revision()).isEqualTo(17);
        assertThat(same.applied()).isFalse();
        assertThat(s.getSnapshot()).isNull();
    }

    @Test
    void should_throw_conflict_when_snapshot_sent_after_end() {
        LiveSession s = session(owner, LiveSessionStatus.ENDED, 3);
        when(repository.findByIdForUpdate(s.getId())).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.updateSnapshot(s.getId(), new LiveSnapshotRequest(4L, simpleSnapshot())))
                .isInstanceOf(LiveSessionEndedException.class);
    }

    @Test
    void should_throw_forbidden_when_snapshot_sent_by_other_user() {
        LiveSession s = session(other, LiveSessionStatus.LIVE, 3);
        when(repository.findByIdForUpdate(s.getId())).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.updateSnapshot(s.getId(), new LiveSnapshotRequest(4L, simpleSnapshot())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void should_throw_bad_request_when_snapshot_payload_invalid() {
        LiveSession s = session(owner, LiveSessionStatus.LIVE, 3);
        when(repository.findByIdForUpdate(s.getId())).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.updateSnapshot(s.getId(),
                new LiveSnapshotRequest(4L, snapshot(List.of(set(9, null, List.of()))))))
                .isInstanceOf(BadRequestException.class);
        assertThat(s.getRevision()).isEqualTo(3);
    }

    @Test
    void should_throw_not_found_when_session_expired() {
        LiveSession s = session(owner, LiveSessionStatus.LIVE, 3);
        s.setExpiresAt(NOW.minusSeconds(1));
        when(repository.findByIdForUpdate(s.getId())).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.updateSnapshot(s.getId(), new LiveSnapshotRequest(4L, simpleSnapshot())))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void should_end_session_idempotently() {
        LiveSession s = session(owner, LiveSessionStatus.LIVE, 3);
        when(repository.findById(s.getId())).thenReturn(Optional.of(s));

        service.end(s.getId());
        Instant firstEndedAt = s.getEndedAt();
        LiveSessionDto second = service.end(s.getId());

        assertThat(second.status()).isEqualTo(LiveSessionStatus.ENDED);
        assertThat(firstEndedAt).isEqualTo(NOW);
        assertThat(s.getEndedAt()).isEqualTo(firstEndedAt);
        assertThat(s.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void should_update_only_showPlayerNames_when_patched() {
        LiveSession s = session(owner, LiveSessionStatus.LIVE, 3);
        when(repository.findById(s.getId())).thenReturn(Optional.of(s));

        LiveSessionDto dto = service.update(s.getId(), new UpdateLiveSessionRequest(true));

        assertThat(dto.showPlayerNames()).isTrue();
        assertThat(dto.title()).isEqualTo("Les Lynx vs Grenoble");
    }

    @Test
    void should_end_and_link_match_when_attached_by_owner() {
        LiveSession s = session(owner, LiveSessionStatus.LIVE, 3);
        when(repository.findById(s.getId())).thenReturn(Optional.of(s));

        service.attachMatch(s.getId(), "match-1", owner);

        assertThat(s.getMatchId()).isEqualTo("match-1");
        assertThat(s.getStatus()).isEqualTo(LiveSessionStatus.ENDED);
    }

    @Test
    void should_ignore_attach_when_session_belongs_to_other_user() {
        LiveSession s = session(other, LiveSessionStatus.LIVE, 3);
        when(repository.findById(s.getId())).thenReturn(Optional.of(s));

        service.attachMatch(s.getId(), "match-1", owner);

        assertThat(s.getMatchId()).isNull();
        assertThat(s.getStatus()).isEqualTo(LiveSessionStatus.LIVE);
    }
}
