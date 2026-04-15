package vasconcelos.silvio.volleymatch.model.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vasconcelos.silvio.volleymatch.model.team.Team;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "players")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "role", nullable = false, length = 30)
    private String role;

    @Column(name = "numero", nullable = false)
    private Integer numero;

    @Column(name = "age", nullable = false)
    private Integer age;

    @Column(name = "taille", nullable = false, length = 10)
    private String taille;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "player_teams",
            joinColumns = @JoinColumn(name = "player_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id")
    )
    @Builder.Default
    private List<Team> teams = new ArrayList<>();

    public void addTeam(Team team) {
        this.teams.add(team);
    }

    public void removeTeam(Team team) {
        this.teams.remove(team);
    }
}