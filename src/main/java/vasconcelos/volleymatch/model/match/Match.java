package vasconcelos.volleymatch.model.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
import vasconcelos.volleymatch.model.user.AppUser;

import java.time.LocalDate;

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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    @Setter
    private AppUser user;
}
