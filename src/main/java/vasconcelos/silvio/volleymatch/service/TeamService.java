package vasconcelos.silvio.volleymatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.silvio.volleymatch.dto.team.CreateTeamRequest;
import vasconcelos.silvio.volleymatch.dto.team.TeamDto;
import vasconcelos.silvio.volleymatch.mapper.TeamMapper;
import vasconcelos.silvio.volleymatch.model.team.Team;
import vasconcelos.silvio.volleymatch.repository.TeamRepository;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teamRepository;
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
}
