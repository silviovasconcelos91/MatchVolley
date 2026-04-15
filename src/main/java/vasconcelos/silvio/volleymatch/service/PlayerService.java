package vasconcelos.silvio.volleymatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.silvio.volleymatch.dto.player.AssignTeamRequest;
import vasconcelos.silvio.volleymatch.dto.player.CreatePlayerRequest;
import vasconcelos.silvio.volleymatch.dto.player.PlayerDto;
import vasconcelos.silvio.volleymatch.mapper.PlayerMapper;
import vasconcelos.silvio.volleymatch.model.player.Player;
import vasconcelos.silvio.volleymatch.model.team.Team;
import vasconcelos.silvio.volleymatch.repository.PlayerRepository;
import vasconcelos.silvio.volleymatch.repository.TeamRepository;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final PlayerMapper playerMapper;

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
        Player saved = playerRepository.save(playerMapper.toEntity(request));
        return playerMapper.toDto(saved);
    }

    @Transactional
    public void deletePlayer(Long id) {
        if (!playerRepository.existsById(id)) {
            throw new NoSuchElementException("Player not found: " + id);
        }
        playerRepository.deleteById(id);
    }

    @Transactional
    public PlayerDto assignTeam(Long playerId, AssignTeamRequest request) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NoSuchElementException("Player not found: " + playerId));
        Team team = teamRepository.findById(request.teamId())
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + request.teamId()));
        player.addTeam(team);
        return playerMapper.toDto(player);
    }

    @Transactional
    public PlayerDto removeTeam(Long playerId, Long teamId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NoSuchElementException("Player not found: " + playerId));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + teamId));
        player.removeTeam(team);
        return playerMapper.toDto(player);
    }
}