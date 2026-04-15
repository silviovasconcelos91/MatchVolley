package vasconcelos.silvio.volleymatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.silvio.volleymatch.dto.match.MatchStatRequest;
import vasconcelos.silvio.volleymatch.dto.match.MatchStatResponse;
import vasconcelos.silvio.volleymatch.mapper.MatchStatMapper;
import vasconcelos.silvio.volleymatch.model.match.Match;
import vasconcelos.silvio.volleymatch.repository.MatchRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class MatchStatService {

    private final MatchRepository matchRepository;
    private final MatchStatMapper matchStatMapper;

    public MatchStatResponse saveMatchStat(MatchStatRequest request) {
        Match match = matchStatMapper.toEntity(request);
        Match saved = matchRepository.save(match);
        return new MatchStatResponse(saved.getId());
    }
}
