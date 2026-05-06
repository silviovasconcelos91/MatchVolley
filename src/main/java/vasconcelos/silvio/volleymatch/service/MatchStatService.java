package vasconcelos.silvio.volleymatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vasconcelos.silvio.volleymatch.dto.match.MatchDetailResponse;
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

    @Transactional(readOnly = true)
    public MatchDetailResponse getMatchStat(String matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found: " + matchId));
        return matchStatMapper.toMatchDetailResponse(match);
    }
}
