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
