package vasconcelos.silvio.volleymatch.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vasconcelos.silvio.volleymatch.dto.common.ApiResponse;
import vasconcelos.silvio.volleymatch.dto.player.AssignTeamRequest;
import vasconcelos.silvio.volleymatch.dto.player.CreatePlayerRequest;
import vasconcelos.silvio.volleymatch.dto.player.PlayerDto;
import vasconcelos.silvio.volleymatch.service.PlayerService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PlayerDto>>> getAllPlayers() {
        List<PlayerDto> players = playerService.getAllPlayers();
        return ResponseEntity.ok(ApiResponse.<List<PlayerDto>>builder()
                .data(players)
                .message("Players retrieved successfully")
                .status(HttpStatus.OK.value())
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlayerDto>> getPlayer(@PathVariable Long id) {
        PlayerDto player = playerService.getPlayer(id);
        return ResponseEntity.ok(ApiResponse.<PlayerDto>builder()
                .data(player)
                .message("Player retrieved successfully")
                .status(HttpStatus.OK.value())
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PlayerDto>> createPlayer(@RequestBody CreatePlayerRequest request) {
        PlayerDto created = playerService.createPlayer(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<PlayerDto>builder()
                        .data(created)
                        .message("Player created successfully")
                        .status(HttpStatus.CREATED.value())
                        .build());
    }

    @PostMapping("/{id}:assignTeam")
    public ResponseEntity<ApiResponse<PlayerDto>> assignTeam(
            @PathVariable Long id,
            @RequestBody AssignTeamRequest request) {
        PlayerDto updated = playerService.assignTeam(id, request);
        return ResponseEntity.ok(ApiResponse.<PlayerDto>builder()
                .data(updated)
                .message("Team assigned successfully")
                .status(HttpStatus.OK.value())
                .build());
    }

    @PostMapping("/{id}:removeTeam")
    public ResponseEntity<ApiResponse<PlayerDto>> removeTeam(
            @PathVariable Long id,
            @RequestBody AssignTeamRequest request) {
        PlayerDto updated = playerService.removeTeam(id, request.teamId());
        return ResponseEntity.ok(ApiResponse.<PlayerDto>builder()
                .data(updated)
                .message("Team removed successfully")
                .status(HttpStatus.OK.value())
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePlayer(@PathVariable Long id) {
        playerService.deletePlayer(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .data(null)
                .message("Player deleted successfully")
                .status(HttpStatus.OK.value())
                .build());
    }
}
