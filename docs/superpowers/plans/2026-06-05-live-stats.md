# Live Stats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose `POST /api/v1/matches/{matchId}/live-stats` to receive and persist raw volleyball event data, and `GET /api/v1/matches/{matchId}/live-stats` to return the full reconstructed payload.

**Architecture:** The live-stats payload creates a new `Match` entity (fields derived from payload) plus a `MatchLiveSession` entity (metadata + back-reference to Match) and N `MatchLiveSetEvent` entities (one per set, events stored as JSONB). The GET reconstructs the original payload from these entities. Stats are computed client-side from the raw events.

**Tech Stack:** Java, Spring Boot 4, JPA/Hibernate, PostgreSQL, Flyway, MapStruct, Lombok, JUnit 5, AssertJ, Mockito

---

## File Map

| Action | Path | Purpose |
|---|---|---|
| Create | `src/main/resources/db/migration/V6__add_live_stats.sql` | New tables |
| Create | `src/main/java/vasconcelos/volleymatch/dto/live/SetsWonDto.java` | DTO |
| Create | `src/main/java/vasconcelos/volleymatch/dto/live/LivePlayerDto.java` | DTO |
| Create | `src/main/java/vasconcelos/volleymatch/dto/live/LiveActionDto.java` | DTO |
| Create | `src/main/java/vasconcelos/volleymatch/dto/live/LiveTrajectoryDto.java` | DTO |
| Create | `src/main/java/vasconcelos/volleymatch/dto/live/LiveEventDto.java` | DTO |
| Create | `src/main/java/vasconcelos/volleymatch/dto/live/LiveSetDto.java` | DTO |
| Create | `src/main/java/vasconcelos/volleymatch/dto/live/LiveStatsRequest.java` | Request DTO |
| Create | `src/main/java/vasconcelos/volleymatch/dto/live/LiveStatsResponse.java` | Response DTO |
| Create | `src/main/java/vasconcelos/volleymatch/dto/live/LiveStatsSavedResponse.java` | POST response DTO |
| Create | `src/main/java/vasconcelos/volleymatch/model/match/MatchLiveSession.java` | JPA entity |
| Create | `src/main/java/vasconcelos/volleymatch/model/match/MatchLiveSetEvent.java` | JPA entity |
| Create | `src/main/java/vasconcelos/volleymatch/repository/MatchLiveSessionRepository.java` | Repository |
| Create | `src/main/java/vasconcelos/volleymatch/mapper/LiveStatsMapper.java` | MapStruct mapper |
| Create | `src/main/java/vasconcelos/volleymatch/service/LiveStatsService.java` | Business logic |
| Modify | `src/main/java/vasconcelos/volleymatch/controller/MatchStatController.java` | Add 2 endpoints |
| Create | `src/test/java/vasconcelos/volleymatch/LiveStatsServiceTest.java` | Unit tests |
| Create | `src/test/java/vasconcelos/volleymatch/LiveStatsControllerIT.java` | Integration tests |

---

## Task 1: DB Migration

**Files:**
- Create: `src/main/resources/db/migration/V6__add_live_stats.sql`

- [ ] **Step 1: Create migration file**

```sql
CREATE TABLE match_live_sessions
(
    id            BIGSERIAL    PRIMARY KEY,
    match_id      VARCHAR(255) NOT NULL UNIQUE REFERENCES matches (id),
    team_name     VARCHAR(255) NOT NULL,
    recorded_at   TIMESTAMP    NOT NULL,
    sets_won_mine INTEGER      NOT NULL,
    sets_won_opp  INTEGER      NOT NULL
);

CREATE TABLE match_live_set_events
(
    id          BIGSERIAL    PRIMARY KEY,
    session_id  BIGINT       NOT NULL REFERENCES match_live_sessions (id),
    set_number  INTEGER      NOT NULL,
    score_team  INTEGER      NOT NULL,
    score_opp   INTEGER      NOT NULL,
    won_by      VARCHAR(4),
    events      JSONB        NOT NULL
);
```

- [ ] **Step 2: Verify migration runs**

```bash
./mvnw flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/volleymatch -Dflyway.user=<user> -Dflyway.password=<pass>
```

Expected: `Successfully applied 1 migration to schema "public"`

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V6__add_live_stats.sql
git commit -m "feat: add live stats DB tables (V6 migration)"
```

---

## Task 2: Live DTOs

**Files:**
- Create: `src/main/java/vasconcelos/volleymatch/dto/live/*.java` (9 files)

- [ ] **Step 1: Create all DTO records**

`src/main/java/vasconcelos/volleymatch/dto/live/SetsWonDto.java`
```java
package vasconcelos.volleymatch.dto.live;

public record SetsWonDto(Integer mine, Integer opp) {}
```

`src/main/java/vasconcelos/volleymatch/dto/live/LivePlayerDto.java`
```java
package vasconcelos.volleymatch.dto.live;

public record LivePlayerDto(Long id, Integer jersey, String name, Integer position) {}
```

`src/main/java/vasconcelos/volleymatch/dto/live/LiveActionDto.java`
```java
package vasconcelos.volleymatch.dto.live;

public record LiveActionDto(String key, String label, String category) {}
```

`src/main/java/vasconcelos/volleymatch/dto/live/LiveTrajectoryDto.java`
```java
package vasconcelos.volleymatch.dto.live;

public record LiveTrajectoryDto(Integer from, Integer to) {}
```

`src/main/java/vasconcelos/volleymatch/dto/live/LiveEventDto.java`
```java
package vasconcelos.volleymatch.dto.live;

public record LiveEventDto(
        String id,
        Integer sequence,
        Long ts,
        String team,
        LivePlayerDto player,
        LiveActionDto action,
        LiveTrajectoryDto trajectory,
        String scoredFor
) {}
```

`src/main/java/vasconcelos/volleymatch/dto/live/LiveSetDto.java`
```java
package vasconcelos.volleymatch.dto.live;

import java.util.List;

public record LiveSetDto(
        Integer setNumber,
        Integer scoreTeam,
        Integer scoreOpp,
        String wonBy,
        List<LiveEventDto> events
) {}
```

`src/main/java/vasconcelos/volleymatch/dto/live/LiveStatsRequest.java`
```java
package vasconcelos.volleymatch.dto.live;

import java.time.Instant;
import java.util.List;

public record LiveStatsRequest(
        String matchId,
        Long teamId,
        String teamName,
        String venue,
        Instant recordedAt,
        SetsWonDto setsWon,
        List<LiveSetDto> sets
) {}
```

`src/main/java/vasconcelos/volleymatch/dto/live/LiveStatsResponse.java`
```java
package vasconcelos.volleymatch.dto.live;

import java.time.Instant;
import java.util.List;

public record LiveStatsResponse(
        String matchId,
        Long teamId,
        String teamName,
        String venue,
        Instant recordedAt,
        SetsWonDto setsWon,
        List<LiveSetDto> sets
) {}
```

`src/main/java/vasconcelos/volleymatch/dto/live/LiveStatsSavedResponse.java`
```java
package vasconcelos.volleymatch.dto.live;

public record LiveStatsSavedResponse(String matchId) {}
```

- [ ] **Step 2: Compile to verify no errors**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/vasconcelos/volleymatch/dto/live/
git commit -m "feat: add live stats DTOs"
```

---

## Task 3: JPA Entities and Repository

**Files:**
- Create: `src/main/java/vasconcelos/volleymatch/model/match/MatchLiveSession.java`
- Create: `src/main/java/vasconcelos/volleymatch/model/match/MatchLiveSetEvent.java`
- Create: `src/main/java/vasconcelos/volleymatch/repository/MatchLiveSessionRepository.java`

- [ ] **Step 1: Create MatchLiveSession entity**

`src/main/java/vasconcelos/volleymatch/model/match/MatchLiveSession.java`
```java
package vasconcelos.volleymatch.model.match;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ConcreteProxy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "match_live_sessions")
@ConcreteProxy
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchLiveSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(name = "team_name", nullable = false)
    private String teamName;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "sets_won_mine", nullable = false)
    private Integer setsWonMine;

    @Column(name = "sets_won_opp", nullable = false)
    private Integer setsWonOpp;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MatchLiveSetEvent> sets = new ArrayList<>();
}
```

- [ ] **Step 2: Create MatchLiveSetEvent entity**

`src/main/java/vasconcelos/volleymatch/model/match/MatchLiveSetEvent.java`
```java
package vasconcelos.volleymatch.model.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ConcreteProxy;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vasconcelos.volleymatch.dto.live.LiveEventDto;

import java.util.List;

@Entity
@Table(name = "match_live_set_events")
@ConcreteProxy
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchLiveSetEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "session_id", nullable = false)
    private MatchLiveSession session;

    @Column(name = "set_number", nullable = false)
    private Integer setNumber;

    @Column(name = "score_team", nullable = false)
    private Integer scoreTeam;

    @Column(name = "score_opp", nullable = false)
    private Integer scoreOpp;

    @Column(name = "won_by", length = 4)
    private String wonBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "events", columnDefinition = "jsonb")
    private List<LiveEventDto> events;
}
```

- [ ] **Step 3: Create repository**

`src/main/java/vasconcelos/volleymatch/repository/MatchLiveSessionRepository.java`
```java
package vasconcelos.volleymatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vasconcelos.volleymatch.model.match.MatchLiveSession;
import vasconcelos.volleymatch.model.user.AppUser;

import java.util.Optional;

public interface MatchLiveSessionRepository extends JpaRepository<MatchLiveSession, Long> {
    Optional<MatchLiveSession> findByMatch_IdAndMatch_User(String matchId, AppUser user);
}
```

- [ ] **Step 4: Compile to verify no errors**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/vasconcelos/volleymatch/model/match/MatchLiveSession.java \
        src/main/java/vasconcelos/volleymatch/model/match/MatchLiveSetEvent.java \
        src/main/java/vasconcelos/volleymatch/repository/MatchLiveSessionRepository.java
git commit -m "feat: add MatchLiveSession and MatchLiveSetEvent entities"
```

---

## Task 4: Write Failing Service Unit Tests

**Files:**
- Create: `src/test/java/vasconcelos/volleymatch/LiveStatsServiceTest.java`

- [ ] **Step 1: Write the failing tests**

`src/test/java/vasconcelos/volleymatch/LiveStatsServiceTest.java`
```java
package vasconcelos.volleymatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
```

- [ ] **Step 2: Run to verify it fails (class not found)**

```bash
./mvnw test -pl . -Dtest=LiveStatsServiceTest -q
```

Expected: FAILURE — `LiveStatsService` does not exist yet

- [ ] **Step 3: Commit**

```bash
git add src/test/java/vasconcelos/volleymatch/LiveStatsServiceTest.java
git commit -m "test: add failing LiveStatsServiceTest"
```

---

## Task 5: Mapper and Service

**Files:**
- Create: `src/main/java/vasconcelos/volleymatch/mapper/LiveStatsMapper.java`
- Create: `src/main/java/vasconcelos/volleymatch/service/LiveStatsService.java`

- [ ] **Step 1: Create LiveStatsMapper**

`src/main/java/vasconcelos/volleymatch/mapper/LiveStatsMapper.java`
```java
package vasconcelos.volleymatch.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vasconcelos.volleymatch.dto.live.LiveEventDto;
import vasconcelos.volleymatch.dto.live.LiveSetDto;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.dto.live.LiveStatsResponse;
import vasconcelos.volleymatch.dto.live.SetsWonDto;
import vasconcelos.volleymatch.model.match.Match;
import vasconcelos.volleymatch.model.match.MatchLiveSession;
import vasconcelos.volleymatch.model.match.MatchLiveSetEvent;
import vasconcelos.volleymatch.model.match.MatchResult;

import java.time.ZoneOffset;

@Mapper(componentModel = "spring", imports = {MatchResult.class, ZoneOffset.class, SetsWonDto.class})
public interface LiveStatsMapper {

    @Mapping(target = "id", source = "matchId")
    @Mapping(target = "home", expression = "java(\"home\".equals(request.venue()))")
    @Mapping(target = "date", expression = "java(request.recordedAt().atZone(ZoneOffset.UTC).toLocalDate())")
    @Mapping(target = "mySets", source = "setsWon.mine")
    @Mapping(target = "oppSets", source = "setsWon.opp")
    @Mapping(target = "result", expression = "java(request.setsWon().mine() > request.setsWon().opp() ? MatchResult.WON : MatchResult.LOST)")
    @Mapping(target = "opponentId", ignore = true)
    @Mapping(target = "seasonId", ignore = true)
    @Mapping(target = "competitionId", ignore = true)
    @Mapping(target = "title", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "teamMatchStats", ignore = true)
    @Mapping(target = "sets", ignore = true)
    @Mapping(target = "players", ignore = true)
    Match toMatchEntity(LiveStatsRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "match", ignore = true)
    @Mapping(target = "setsWonMine", source = "setsWon.mine")
    @Mapping(target = "setsWonOpp", source = "setsWon.opp")
    @Mapping(target = "sets", source = "sets")
    MatchLiveSession toSessionEntity(LiveStatsRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "session", ignore = true)
    MatchLiveSetEvent toSetEventEntity(LiveSetDto dto);

    @Mapping(target = "matchId", source = "match.id")
    @Mapping(target = "teamId", source = "match.teamId")
    @Mapping(target = "venue", expression = "java(session.getMatch().getHome() ? \"home\" : \"away\")")
    @Mapping(target = "setsWon", expression = "java(new SetsWonDto(session.getSetsWonMine(), session.getSetsWonOpp()))")
    @Mapping(target = "sets", source = "sets")
    LiveStatsResponse toResponse(MatchLiveSession session);

    LiveSetDto toSetDto(MatchLiveSetEvent setEvent);

    @AfterMapping
    default void wireSetBackReferences(@MappingTarget MatchLiveSession session) {
        if (session.getSets() != null) {
            session.getSets().forEach(s -> s.setSession(session));
        }
    }
}
```

- [ ] **Step 2: Create LiveStatsService**

`src/main/java/vasconcelos/volleymatch/service/LiveStatsService.java`
```java
package vasconcelos.volleymatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.volleymatch.dto.live.LiveStatsSavedResponse;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.dto.live.LiveStatsResponse;
import vasconcelos.volleymatch.mapper.LiveStatsMapper;
import vasconcelos.volleymatch.model.match.Match;
import vasconcelos.volleymatch.model.match.MatchLiveSession;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.repository.MatchLiveSessionRepository;
import vasconcelos.volleymatch.repository.MatchRepository;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional
public class LiveStatsService {

    private final MatchRepository matchRepository;
    private final MatchLiveSessionRepository liveSessionRepository;
    private final LiveStatsMapper liveStatsMapper;
    private final AuthService authService;

    public LiveStatsSavedResponse saveLiveStats(LiveStatsRequest request) {
        if (matchRepository.existsById(request.matchId())) {
            throw new IllegalArgumentException("Match already exists: " + request.matchId());
        }
        AppUser user = authService.getCurrentUser();
        Match match = liveStatsMapper.toMatchEntity(request);
        match.setUser(user);
        matchRepository.save(match);

        MatchLiveSession session = liveStatsMapper.toSessionEntity(request);
        session.setMatch(match);
        liveSessionRepository.save(session);

        return new LiveStatsSavedResponse(match.getId());
    }

    @Transactional(readOnly = true)
    public LiveStatsResponse getLiveStats(String matchId) {
        AppUser user = authService.getCurrentUser();
        MatchLiveSession session = liveSessionRepository
                .findByMatch_IdAndMatch_User(matchId, user)
                .orElseThrow(() -> new NoSuchElementException("Match not found: " + matchId));
        return liveStatsMapper.toResponse(session);
    }
}
```

- [ ] **Step 3: Run unit tests — they must pass**

```bash
./mvnw test -pl . -Dtest=LiveStatsServiceTest -q
```

Expected: BUILD SUCCESS, 4 tests pass

- [ ] **Step 4: Commit**

```bash
git add src/main/java/vasconcelos/volleymatch/mapper/LiveStatsMapper.java \
        src/main/java/vasconcelos/volleymatch/service/LiveStatsService.java
git commit -m "feat: add LiveStatsMapper and LiveStatsService"
```

---

## Task 6: Controller Endpoints

**Files:**
- Modify: `src/main/java/vasconcelos/volleymatch/controller/MatchStatController.java`

- [ ] **Step 1: Add the two endpoints to MatchStatController**

Replace the content of `MatchStatController.java` with:
```java
package vasconcelos.volleymatch.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vasconcelos.volleymatch.dto.common.ApiResponse;
import vasconcelos.volleymatch.dto.live.LiveStatsSavedResponse;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.dto.live.LiveStatsResponse;
import vasconcelos.volleymatch.dto.match.MatchDetailResponse;
import vasconcelos.volleymatch.dto.match.MatchStatRequest;
import vasconcelos.volleymatch.dto.match.MatchStatResponse;
import vasconcelos.volleymatch.service.LiveStatsService;
import vasconcelos.volleymatch.service.MatchStatService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MatchStatController {

    private final MatchStatService matchStatService;
    private final LiveStatsService liveStatsService;

    @PostMapping("/match-stats")
    public ResponseEntity<ApiResponse<MatchStatResponse>> saveMatchStat(
            @RequestBody MatchStatRequest request) {

        MatchStatResponse data = matchStatService.saveMatchStat(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.<MatchStatResponse>builder()
                        .data(data)
                        .message("Match stats saved successfully")
                        .status(HttpStatus.CREATED.value())
                        .build());
    }

    @GetMapping("/match-stats/{matchId}")
    public ResponseEntity<ApiResponse<MatchDetailResponse>> getMatchStat(
            @PathVariable String matchId) {

        MatchDetailResponse data = matchStatService.getMatchStat(matchId);

        return ResponseEntity.ok(ApiResponse.<MatchDetailResponse>builder()
                .data(data)
                .message("Match stats retrieved successfully")
                .status(HttpStatus.OK.value())
                .build());
    }

    @PostMapping("/matches/{matchId}/live-stats")
    public ResponseEntity<ApiResponse<LiveStatsSavedResponse>> saveLiveStats(
            @PathVariable String matchId,
            @RequestBody LiveStatsRequest request) {

        LiveStatsSavedResponse data = liveStatsService.saveLiveStats(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.<LiveStatsSavedResponse>builder()
                        .data(data)
                        .message("Live stats saved successfully")
                        .status(HttpStatus.CREATED.value())
                        .build());
    }

    @GetMapping("/matches/{matchId}/live-stats")
    public ResponseEntity<ApiResponse<LiveStatsResponse>> getLiveStats(
            @PathVariable String matchId) {

        LiveStatsResponse data = liveStatsService.getLiveStats(matchId);

        return ResponseEntity.ok(ApiResponse.<LiveStatsResponse>builder()
                .data(data)
                .message("Live stats retrieved successfully")
                .status(HttpStatus.OK.value())
                .build());
    }
}
```

- [ ] **Step 2: Compile to verify no errors**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/vasconcelos/volleymatch/controller/MatchStatController.java
git commit -m "feat: add POST and GET live-stats endpoints"
```

---

## Task 7: Integration Tests

**Files:**
- Create: `src/test/java/vasconcelos/volleymatch/LiveStatsControllerIT.java`

- [ ] **Step 1: Write the failing integration tests**

`src/test/java/vasconcelos/volleymatch/LiveStatsControllerIT.java`
```java
package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
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
                new org.springframework.http.HttpEntity<>(buildValidRequest("live-unauth")),
                ApiResponse.class);

        ResponseEntity<ApiResponse> getResponse = restTemplate.exchange(
                "/api/v1/matches/live-unauth/live-stats",
                HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(null),
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
```

- [ ] **Step 2: Run all tests**

```bash
./mvnw test -q
```

Expected: BUILD SUCCESS — all tests pass including the 6 new IT tests and 4 unit tests

- [ ] **Step 3: Commit**

```bash
git add src/test/java/vasconcelos/volleymatch/LiveStatsControllerIT.java
git commit -m "test: add LiveStatsControllerIT integration tests"
```

---

## Self-Review Checklist

- [x] **Spec coverage:** POST endpoint ✓, GET endpoint ✓, 409 on duplicate ✓, 404 on missing ✓, 401 unauthenticated ✓, JSONB events storage ✓, Match entity derived fields ✓, result WIN/LOSS derivation ✓
- [x] **No placeholders:** All steps have complete code
- [x] **Type consistency:** `LiveStatsSavedResponse(matchId)` consistent across service/controller/test. `findByMatch_IdAndMatch_User` consistent in repo and service. `liveStatsMapper.toMatchEntity/toSessionEntity/toResponse` consistent across mapper/service/test
- [x] **409 routing:** `IllegalArgumentException` → existing `GlobalExceptionHandler.handleConflict` → 409. No new exception class needed.
