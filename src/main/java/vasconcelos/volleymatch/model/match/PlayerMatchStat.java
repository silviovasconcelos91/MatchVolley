package vasconcelos.volleymatch.model.match;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ConcreteProxy;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "player_match_stats")
@ConcreteProxy
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlayerMatchStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "number", nullable = false)
    private Integer number;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private VolleyPosition role;

    @Embedded
    private VolleyStats matchStats;

    @OneToMany(mappedBy = "playerMatchStat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlayerSetStat> setStats = new ArrayList<>();
}
