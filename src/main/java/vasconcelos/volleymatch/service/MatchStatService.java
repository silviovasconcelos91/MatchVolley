package vasconcelos.volleymatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.volleymatch.dto.match.MatchDetailResponse;
import vasconcelos.volleymatch.dto.match.MatchStatRequest;
import vasconcelos.volleymatch.dto.match.MatchStatResponse;
import vasconcelos.volleymatch.mapper.MatchStatMapper;
import vasconcelos.volleymatch.model.match.Match;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.repository.MatchRepository;

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
