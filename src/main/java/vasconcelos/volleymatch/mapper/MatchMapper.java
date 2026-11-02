package vasconcelos.volleymatch.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vasconcelos.volleymatch.dto.match.MatchDto;
import vasconcelos.volleymatch.model.match.Match;

@Mapper(componentModel = "spring")
public interface MatchMapper {

    @Mapping(target = "result", expression = "java(match.getResult() != null ? match.getResult().name().toLowerCase() : null)")
    MatchDto toMatchDto(Match match);
}
