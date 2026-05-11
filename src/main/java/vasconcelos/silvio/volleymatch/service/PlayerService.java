package vasconcelos.silvio.volleymatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.silvio.volleymatch.dto.match.StatsDto;
import vasconcelos.silvio.volleymatch.dto.player.CreatePlayerRequest;
import vasconcelos.silvio.volleymatch.dto.player.PlayerDto;
import vasconcelos.silvio.volleymatch.dto.player.PlayerSeasonStatsResponse;
import vasconcelos.silvio.volleymatch.dto.player.PositionStatsDto;
import vasconcelos.silvio.volleymatch.dto.player.UpdatePlayerRequest;
import vasconcelos.silvio.volleymatch.mapper.PlayerMapper;
import vasconcelos.silvio.volleymatch.model.match.PlayerMatchStat;
import vasconcelos.silvio.volleymatch.model.match.PlayerSetStat;
import vasconcelos.silvio.volleymatch.model.match.VolleyStats;
import vasconcelos.silvio.volleymatch.model.player.Player;
import vasconcelos.silvio.volleymatch.model.team.Team;
import vasconcelos.silvio.volleymatch.repository.PlayerMatchStatRepository;
import vasconcelos.silvio.volleymatch.repository.PlayerRepository;
import vasconcelos.silvio.volleymatch.repository.TeamRepository;

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
    private final PlayerMatchStatRepository playerMatchStatRepository;
    private final PlayerMapper playerMapper;

    public PlayerSeasonStatsResponse getPlayerSeasonStats(Long playerId, Long teamId) {
        if (!playerRepository.existsById(playerId)) {
            throw new NoSuchElementException("Player not found: " + playerId);
        }
        if (!teamRepository.existsById(teamId)) {
            throw new NoSuchElementException("Team not found: " + teamId);
        }
        List<PlayerMatchStat> matchStats = playerMatchStatRepository.findByPlayerIdAndMatchTeamId(playerId, teamId);

        List<PlayerSetStat> allSetStats = matchStats.stream()
                .flatMap(m -> m.getSetStats().stream())
                .toList();

        StatsDto total = sumVolleyStats(
                matchStats.stream().map(PlayerMatchStat::getMatchStats).toList()
        );

        Map<String, PositionStatsDto> byPosition = matchStats.stream()
                .flatMap(m -> m.getSetStats().stream()
                        .filter(s -> s.getPosition() != null)
                        .map(s -> Map.entry(m, s)))
                .collect(Collectors.groupingBy(
                        e -> e.getValue().getPosition().name(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                entries -> new PositionStatsDto(
                                        (int) entries.stream().map(e -> e.getKey().getId()).distinct().count(),
                                        entries.size(),
                                        sumVolleyStats(entries.stream().map(e -> e.getValue().getStats()).toList())
                                )
                        )
                ));

        return new PlayerSeasonStatsResponse(playerId, matchStats.size(), allSetStats.size(), total, byPosition);
    }

    private StatsDto sumVolleyStats(List<VolleyStats> statsList) {
        int points = 0, attackPoints = 0, blockPoints = 0, acePoints = 0;
        int attackErrors = 0, serviceErrors = 0, receptions = 0;
        for (VolleyStats s : statsList) {
            if (s.getPoints() != null) points += s.getPoints();
            if (s.getAttackPoints() != null) attackPoints += s.getAttackPoints();
            if (s.getBlockPoints() != null) blockPoints += s.getBlockPoints();
            if (s.getAcePoints() != null) acePoints += s.getAcePoints();
            if (s.getAttackErrors() != null) attackErrors += s.getAttackErrors();
            if (s.getServiceErrors() != null) serviceErrors += s.getServiceErrors();
            if (s.getReceptions() != null) receptions += s.getReceptions();
        }
        return new StatsDto(points, attackPoints, blockPoints, acePoints, attackErrors, serviceErrors, receptions);
    }

    public List<PlayerDto> getAllPlayers() {
        return playerRepository.findAll().stream()
                .map(playerMapper::toDto)
                .toList();
    }

    public PlayerDto getPlayer(Long id) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Player not found: " + id));
        return playerMapper.toDto(player);
    }

    @Transactional
    public PlayerDto createPlayer(CreatePlayerRequest request) {
        Player player = playerMapper.toEntity(request);
        if (request.teamIds() != null && !request.teamIds().isEmpty()) {
            List<Team> teams = teamRepository.findAllById(request.teamIds());
            if (teams.size() != request.teamIds().size()) {
                throw new NoSuchElementException("One or more teams not found");
            }
            teams.forEach(player::addTeam);
        }
        return playerMapper.toDto(playerRepository.save(player));
    }

    @Transactional
    public PlayerDto updatePlayer(Long id, UpdatePlayerRequest request) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Player not found: " + id));
        if (request.name() != null) player.setName(request.name());
        if (request.role() != null) player.setRole(request.role());
        if (request.numero() != null) player.setNumero(request.numero());
        if (request.age() != null) player.setAge(request.age());
        if (request.taille() != null) player.setTaille(request.taille());
        if (request.teamIds() != null) {
            List<Team> newTeams = teamRepository.findAllById(request.teamIds());
            if (newTeams.size() != request.teamIds().size()) {
                throw new NoSuchElementException("One or more teams not found");
            }
            Set<Long> newIds = newTeams.stream().map(Team::getId).collect(Collectors.toSet());
            Set<Long> currentIds = player.getTeams().stream().map(Team::getId).collect(Collectors.toSet());

            player.getTeams().stream()
                    .filter(t -> !newIds.contains(t.getId()))
                    .toList()
                    .forEach(player::removeTeam);

            newTeams.stream()
                    .filter(t -> !currentIds.contains(t.getId()))
                    .forEach(player::addTeam);
        }
        return playerMapper.toDto(player);
    }

    @Transactional
    public void deletePlayer(Long id) {
        if (!playerRepository.existsById(id)) {
            throw new NoSuchElementException("Player not found: " + id);
        }
        playerRepository.deleteById(id);
    }
}