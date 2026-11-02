package vasconcelos.volleymatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.volleymatch.dto.match.StatsDto;
import vasconcelos.volleymatch.dto.player.CreatePlayerRequest;
import vasconcelos.volleymatch.dto.player.PlayerDto;
import vasconcelos.volleymatch.dto.player.PlayerSeasonStatsResponse;
import vasconcelos.volleymatch.dto.player.PositionStatsDto;
import vasconcelos.volleymatch.dto.player.UpdatePlayerRequest;
import vasconcelos.volleymatch.mapper.PlayerMapper;
import vasconcelos.volleymatch.dto.live.LiveEventDto;
import vasconcelos.volleymatch.model.match.MatchLiveSession;
import vasconcelos.volleymatch.model.match.MatchLiveSetEvent;
import vasconcelos.volleymatch.model.player.Player;
import vasconcelos.volleymatch.model.team.Team;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.repository.MatchLiveSessionRepository;
import vasconcelos.volleymatch.repository.PlayerRepository;
import vasconcelos.volleymatch.repository.TeamRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final MatchLiveSessionRepository liveSessionRepository;
    private final PlayerMapper playerMapper;
    private final AuthService authService;

    public PlayerSeasonStatsResponse getPlayerSeasonStats(Long playerId, Long teamId) {
        AppUser user = authService.getCurrentUser();
        if (!playerRepository.existsByIdAndUser(playerId, user)) {
            throw new NoSuchElementException("Player not found: " + playerId);
        }
        if (!teamRepository.existsByIdAndUser(teamId, user)) {
            throw new NoSuchElementException("Team not found: " + teamId);
        }
        List<MatchLiveSession> sessions = liveSessionRepository.findByMatch_TeamIdAndMatch_User(teamId, user);

        int matchCount = 0;
        int setCount = 0;
        List<LiveEventDto> allEvents = new ArrayList<>();
        Map<String, List<LiveEventDto>> eventsByPoste = new LinkedHashMap<>();
        Map<String, Set<String>> matchesByPoste = new LinkedHashMap<>();
        Map<String, Integer> setsByPoste = new LinkedHashMap<>();

        for (MatchLiveSession session : sessions) {
            boolean played = false;
            for (MatchLiveSetEvent set : session.getSets()) {
                List<LiveEventDto> playerEvents = playerEventsOf(set, playerId);
                if (playerEvents.isEmpty()) continue;
                played = true;
                setCount++;
                allEvents.addAll(playerEvents);
                String poste = LiveStatsAnalysisService.posteOf(playerEvents);
                if (poste != null) {
                    eventsByPoste.computeIfAbsent(poste, k -> new ArrayList<>()).addAll(playerEvents);
                    matchesByPoste.computeIfAbsent(poste, k -> new HashSet<>()).add(session.getMatch().getId());
                    setsByPoste.merge(poste, 1, Integer::sum);
                }
            }
            if (played) matchCount++;
        }

        Map<String, PositionStatsDto> byPosition = new LinkedHashMap<>();
        eventsByPoste.forEach((poste, events) -> byPosition.put(poste, new PositionStatsDto(
                matchesByPoste.get(poste).size(), setsByPoste.get(poste), toStats(events))));

        return new PlayerSeasonStatsResponse(playerId, matchCount, setCount, toStats(allEvents), byPosition);
    }

    private List<LiveEventDto> playerEventsOf(MatchLiveSetEvent set, Long playerId) {
        if (set.getEvents() == null) return List.of();
        return set.getEvents().stream()
                .filter(e -> "mine".equals(e.team()) && e.player() != null && playerId.equals(e.player().id()))
                .toList();
    }

    /** Aggregates live events into the season stats format. */
    private StatsDto toStats(List<LiveEventDto> events) {
        int points = 0, attackPoints = 0, blockPoints = 0, acePoints = 0;
        int attackErrors = 0, serviceErrors = 0, receptions = 0, faults = 0;
        for (LiveEventDto e : events) {
            if (e.action() == null) continue;
            if ("point".equals(e.action().category())) points++;
            if ("fault".equals(e.action().category())) faults++;
            switch (e.action().key()) {
                case "attack_pt" -> attackPoints++;
                case "block_pt" -> blockPoints++;
                case "ace" -> acePoints++;
                case "attack_fault" -> attackErrors++;
                case "serve_fault" -> serviceErrors++;
                case "good_recv", "bad_recv", "recv_fault" -> receptions++;
                default -> { }
            }
        }
        return new StatsDto(points, attackPoints, blockPoints, acePoints, attackErrors, serviceErrors, receptions, faults);
    }

    public List<PlayerDto> getAllPlayers() {
        AppUser user = authService.getCurrentUser();
        return playerRepository.findAllByUser(user).stream()
                .map(playerMapper::toDto).toList();
    }

    public PlayerDto getPlayer(Long id) {
        AppUser user = authService.getCurrentUser();
        Player player = playerRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new NoSuchElementException("Player not found: " + id));
        return playerMapper.toDto(player);
    }

    @Transactional
    public PlayerDto createPlayer(CreatePlayerRequest request) {
        AppUser user = authService.getCurrentUser();
        Player player = playerMapper.toEntity(request);
        player.setUser(user);
        if (request.teamIds() != null && !request.teamIds().isEmpty()) {
            List<Team> teams = teamRepository.findAllByIdInAndUser(request.teamIds(), user);
            if (teams.size() != request.teamIds().size()) {
                throw new NoSuchElementException("One or more teams not found");
            }
            teams.forEach(player::addTeam);
        }
        return playerMapper.toDto(playerRepository.save(player));
    }

    @Transactional
    public PlayerDto updatePlayer(Long id, UpdatePlayerRequest request) {
        AppUser user = authService.getCurrentUser();
        Player player = playerRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new NoSuchElementException("Player not found: " + id));
        if (request.name() != null) player.setName(request.name());
        if (request.roles() != null) player.setRoles(request.roles());
        if (request.numero() != null) player.setNumero(request.numero());
        if (request.teamIds() != null) {
            List<Team> newTeams = teamRepository.findAllByIdInAndUser(request.teamIds(), user);
            if (newTeams.size() != request.teamIds().size()) {
                throw new NoSuchElementException("One or more teams not found");
            }
            Set<Long> newIds = newTeams.stream().map(Team::getId).collect(Collectors.toSet());
            Set<Long> currentIds = player.getTeams().stream().map(Team::getId).collect(Collectors.toSet());
            player.getTeams().stream().filter(t -> !newIds.contains(t.getId())).toList().forEach(player::removeTeam);
            newTeams.stream().filter(t -> !currentIds.contains(t.getId())).forEach(player::addTeam);
        }
        return playerMapper.toDto(player);
    }

    @Transactional
    public void deletePlayer(Long id) {
        AppUser user = authService.getCurrentUser();
        if (!playerRepository.existsByIdAndUser(id, user)) {
            throw new NoSuchElementException("Player not found: " + id);
        }
        playerRepository.deleteById(id);
    }
}
