package vasconcelos.silvio.volleymatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.silvio.volleymatch.dto.team.AssignPlayersRequest;
import vasconcelos.silvio.volleymatch.dto.team.CreateTeamRequest;
import vasconcelos.silvio.volleymatch.dto.team.RemovePlayersRequest;
import vasconcelos.silvio.volleymatch.dto.team.TeamDto;
import vasconcelos.silvio.volleymatch.mapper.TeamMapper;
import vasconcelos.silvio.volleymatch.model.player.Player;
import vasconcelos.silvio.volleymatch.model.team.Team;
import vasconcelos.silvio.volleymatch.repository.PlayerRepository;
import vasconcelos.silvio.volleymatch.repository.TeamRepository;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final TeamMapper teamMapper;

    public List<TeamDto> getAllTeams() {
        return teamRepository.findAll().stream()
                .map(teamMapper::toDto)
                .toList();
    }

    public TeamDto getTeam(Long id) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + id));
        return teamMapper.toDto(team);
    }

    @Transactional
    public TeamDto createTeam(CreateTeamRequest request) {
        Team saved = teamRepository.save(teamMapper.toEntity(request));
        return teamMapper.toDto(saved);
    }

    @Transactional
    public TeamDto assignPlayers(Long teamId, AssignPlayersRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + teamId));

        List<Player> players = playerRepository.findAllById(request.playerIds());
        if (players.size() != request.playerIds().size()) {
            throw new NoSuchElementException("One or more players not found");
        }

        players.forEach(player -> player.addTeam(team));
        playerRepository.saveAll(players);

        return teamMapper.toDto(teamRepository.findById(teamId).orElseThrow());
    }

    @Transactional
    public TeamDto removePlayers(Long teamId, RemovePlayersRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + teamId));

        List<Player> players = playerRepository.findAllById(request.playerIds());
        if (players.size() != request.playerIds().size()) {
            throw new NoSuchElementException("One or more players not found");
        }

        players.forEach(player -> player.removeTeam(team));
        playerRepository.saveAll(players);

        return teamMapper.toDto(teamRepository.findById(teamId).orElseThrow());
    }
}
