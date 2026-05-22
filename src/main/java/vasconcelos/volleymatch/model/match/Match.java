package vasconcelos.volleymatch.model.match;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ConcreteProxy;
import vasconcelos.volleymatch.model.user.AppUser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "matches")
@ConcreteProxy
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Match {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Builder.Default
    @Column(name = "opponent_id")
    private Long opponentId = 1L;

    @Builder.Default
    @Column(name = "season_id")
    private String seasonId = "2025/2026";

    @Builder.Default
    @Column(name = "competition_id")
    private Long competitionId = 1L;

    @Column(name = "title")
    private String title;

    @Column(name = "home")
    private Boolean home;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 10)
    private MatchResult result;

    @Column(name = "my_sets", nullable = false)
    private Integer mySets;

    @Column(name = "opp_sets", nullable = false)
    private Integer oppSets;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @Setter
    private AppUser user;

    @Embedded
    private VolleyStats teamMatchStats;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SetStat> sets = new ArrayList<>();

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PlayerMatchStat> players = new ArrayList<>();
}
