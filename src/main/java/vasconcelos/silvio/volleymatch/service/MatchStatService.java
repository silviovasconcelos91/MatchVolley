package vasconcelos.silvio.volleymatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.silvio.volleymatch.dto.match.MatchDetailResponse;
import vasconcelos.silvio.volleymatch.dto.match.MatchStatRequest;
import vasconcelos.silvio.volleymatch.dto.match.MatchStatResponse;
import vasconcelos.silvio.volleymatch.mapper.MatchStatMapper;
import vasconcelos.silvio.volleymatch.model.match.Match;
import vasconcelos.silvio.volleymatch.model.user.AppUser;
import vasconcelos.silvio.volleymatch.repository.MatchRepository;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional
public class MatchStatService {

    private final MatchRepository matchRepository;
    private final MatchStatMapper matchStatMapper;
    private final AuthService authService;

    public MatchStatResponse saveMatchStat(MatchStatRequest request) {
        AppUser user = authService.getCurrentUser();
        Match match = matchStatMapper.toEntity(request);
        match.setUser(user);
        return new MatchStatResponse(matchRepository.save(match).getId());
    }

    @Transactional(readOnly = true)
    public MatchDetailResponse getMatchStat(String matchId) {
        AppUser user = authService.getCurrentUser();
        Match match = matchRepository.findByIdAndUser(matchId, user)
                .orElseThrow(() -> new NoSuchElementException("Match not found: " + matchId));
        return matchStatMapper.toMatchDetailResponse(match);
    }
}
