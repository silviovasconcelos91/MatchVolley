package vasconcelos.volleymatch.model.match;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VolleyStats {
    private Integer points;
    private Integer attackPoints;
    private Integer blockPoints;
    private Integer acePoints;
    private Integer attackErrors;
    private Integer serviceErrors;
    private Integer receptions;
    private Integer faults;
}
