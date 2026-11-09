package vasconcelos.volleymatch.model.live;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.model.team.Team;
import vasconcelos.volleymatch.model.user.AppUser;

import java.time.Instant;
import java.util.UUID;

/**
 * Public live broadcast of a match being recorded. Holds the latest full
 * {@link LiveStatsRequest} snapshot pushed by the app, exposed read-only via a 6-char code.
 */
@Entity
@Table(name = "live_sessions")
@ConcreteProxy
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 6)
    private String code;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private AppUser owner;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "venue", nullable = false, length = 4)
    private String venue;

    @Column(name = "show_player_names", nullable = false)
    private boolean showPlayerNames;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 5)
    private LiveSessionStatus status;

    @Column(name = "revision", nullable = false)
    private long revision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", columnDefinition = "jsonb")
    private LiveStatsRequest snapshot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "match_id")
    private String matchId;
}
