package vasconcelos.volleymatch.model.match;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import vasconcelos.volleymatch.dto.match.TimelineEntryDto;

import java.util.List;

@Entity
@Table(name = "set_stats")
@ConcreteProxy
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(name = "set_num", nullable = false)
    private Integer set;

    @Column(name = "my_score", nullable = false)
    private Integer myScore;

    @Column(name = "opp_score", nullable = false)
    private Integer oppScore;

    @Embedded
    private VolleyStats teamStats;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "timeline", columnDefinition = "jsonb")
    private List<TimelineEntryDto> timeline;
}
