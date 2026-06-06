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
    private Integer set;

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
