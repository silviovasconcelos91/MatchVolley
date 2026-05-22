package vasconcelos.volleymatch.model.player;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vasconcelos.volleymatch.model.user.AppUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ConcreteProxy;
import vasconcelos.volleymatch.model.match.VolleyPosition;
import vasconcelos.volleymatch.model.team.Team;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "players")
@ConcreteProxy
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "player_roles", joinColumns = @JoinColumn(name = "player_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    @Builder.Default
    private List<VolleyPosition> roles = new ArrayList<>();

    @Column(name = "numero", nullable = false)
    private Integer numero;

    @Column(name = "age", nullable = false)
    private Integer age;

    @Column(name = "taille", nullable = false, length = 10)
    private String taille;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "player_teams",
            joinColumns = @JoinColumn(name = "player_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id")
    )
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private List<Team> teams = new ArrayList<>();

    public void addTeam(Team team) {
        this.teams.add(team);
    }

    public void removeTeam(Team team) {
        this.teams.remove(team);
    }
}