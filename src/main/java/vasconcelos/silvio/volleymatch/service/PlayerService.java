package vasconcelos.silvio.volleymatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.silvio.volleymatch.dto.player.CreatePlayerRequest;
import vasconcelos.silvio.volleymatch.dto.player.PlayerDto;
import vasconcelos.silvio.volleymatch.dto.player.UpdatePlayerRequest;
import vasconcelos.silvio.volleymatch.mapper.PlayerMapper;
import vasconcelos.silvio.volleymatch.model.player.Player;
import vasconcelos.silvio.volleymatch.model.team.Team;
import vasconcelos.silvio.volleymatch.repository.PlayerRepository;
import vasconcelos.silvio.volleymatch.repository.TeamRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

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
        if (request.name() != null && !request.name().equals(player.getName())) player.setName(request.name());
        if (request.role() != null && !request.role().equals(player.getRole())) player.setRole(request.role());
        if (request.numero() != null && !request.numero().equals(player.getNumero()))
            player.setNumero(request.numero());
        if (request.age() != null && !request.age().equals(player.getAge())) player.setAge(request.age());
        if (request.taille() != null && !request.taille().equals(player.getTaille()))
            player.setTaille(request.taille());
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