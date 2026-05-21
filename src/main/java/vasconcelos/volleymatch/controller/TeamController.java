package vasconcelos.volleymatch.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vasconcelos.volleymatch.dto.common.ApiResponse;
import vasconcelos.volleymatch.dto.match.MatchDto;
import vasconcelos.volleymatch.dto.team.AssignPlayersRequest;
import vasconcelos.volleymatch.dto.team.CreateTeamRequest;
import vasconcelos.volleymatch.dto.team.RemovePlayersRequest;
import vasconcelos.volleymatch.dto.team.TeamDto;
import vasconcelos.volleymatch.service.TeamService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TeamDto>>> getAllTeams() {
        List<TeamDto> teams = teamService.getAllTeams();
        return ResponseEntity.ok(ApiResponse.<List<TeamDto>>builder()
                .data(teams)
                .message("Teams retrieved successfully")
                .status(HttpStatus.OK.value())
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TeamDto>> getTeam(@PathVariable Long id) {
        TeamDto team = teamService.getTeam(id);
        return ResponseEntity.ok(ApiResponse.<TeamDto>builder()
                .data(team)
                .message("Team retrieved successfully")
                .status(HttpStatus.OK.value())
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TeamDto>> createTeam(@RequestBody CreateTeamRequest request) {
        TeamDto created = teamService.createTeam(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TeamDto>builder()
                        .data(created)
                        .message("Team created successfully")
                        .status(HttpStatus.CREATED.value())
                        .build());
    }

    @GetMapping("/{id}/matches")
    public ResponseEntity<ApiResponse<List<MatchDto>>> getTeamMatches(@PathVariable Long id) {
        List<MatchDto> matches = teamService.getMatchesByTeam(id);
        return ResponseEntity.ok(ApiResponse.<List<MatchDto>>builder()
                .data(matches)
                .message("Team matches retrieved successfully")
                .status(HttpStatus.OK.value())
                .build());
    }

    @PostMapping("/{id}:assignPlayers")
    public ResponseEntity<ApiResponse<TeamDto>> assignPlayers(
            @PathVariable Long id,
            @RequestBody AssignPlayersRequest request) {
        TeamDto team = teamService.assignPlayers(id, request);
        return ResponseEntity.ok(ApiResponse.<TeamDto>builder()
                .data(team)
                .message("Players assigned successfully")
                .status(HttpStatus.OK.value())
                .build());
    }

    @PostMapping("/{id}:removePlayers")
    public ResponseEntity<ApiResponse<TeamDto>> removePlayers(
            @PathVariable Long id,
            @RequestBody RemovePlayersRequest request) {
        TeamDto team = teamService.removePlayers(id, request);
        return ResponseEntity.ok(ApiResponse.<TeamDto>builder()
                .data(team)
                .message("Players removed successfully")
                .status(HttpStatus.OK.value())
                .build());
    }
}
