package vasconcelos.volleymatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.volleymatch.dto.live.LiveStatsSavedResponse;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.dto.live.LiveStatsResponse;
import vasconcelos.volleymatch.mapper.LiveStatsMapper;
import vasconcelos.volleymatch.model.match.Match;
import vasconcelos.volleymatch.model.match.MatchLiveSession;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.repository.MatchLiveSessionRepository;
import vasconcelos.volleymatch.repository.MatchRepository;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional
public class LiveStatsService {

    private final MatchRepository matchRepository;
    private final MatchLiveSessionRepository liveSessionRepository;
    private final LiveStatsMapper liveStatsMapper;
    private final AuthService authService;

    public LiveStatsSavedResponse saveLiveStats(LiveStatsRequest request) {
        if (matchRepository.existsById(request.matchId())) {
            throw new IllegalArgumentException("Match already exists: " + request.matchId());
        }
        AppUser user = authService.getCurrentUser();
        Match match = liveStatsMapper.toMatchEntity(request);
        match.setUser(user);
        matchRepository.save(match);

        MatchLiveSession session = liveStatsMapper.toSessionEntity(request);
        session.setMatch(match);
        liveSessionRepository.save(session);

        return new LiveStatsSavedResponse(match.getId());
    }

    @Transactional(readOnly = true)
    public LiveStatsResponse getLiveStats(String matchId) {
        AppUser user = authService.getCurrentUser();
        MatchLiveSession session = liveSessionRepository
                .findByMatch_IdAndMatch_User(matchId, user)
                .orElseThrow(() -> new NoSuchElementException("Match not found: " + matchId));
        return liveStatsMapper.toResponse(session);
    }
}
