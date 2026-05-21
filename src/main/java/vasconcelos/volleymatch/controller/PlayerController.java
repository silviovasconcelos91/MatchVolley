package vasconcelos.volleymatch.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vasconcelos.volleymatch.dto.common.ApiResponse;
import vasconcelos.volleymatch.dto.player.CreatePlayerRequest;
import vasconcelos.volleymatch.dto.player.PlayerDto;
import vasconcelos.volleymatch.dto.player.PlayerSeasonStatsResponse;
import vasconcelos.volleymatch.dto.player.UpdatePlayerRequest;
import vasconcelos.volleymatch.service.PlayerService;

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

    @GetMapping("/{id}/season-stats")
    public ResponseEntity<ApiResponse<PlayerSeasonStatsResponse>> getPlayerSeasonStats(
            @PathVariable Long id,
            @RequestParam Long teamId) {
        PlayerSeasonStatsResponse stats = playerService.getPlayerSeasonStats(id, teamId);
        return ResponseEntity.ok(ApiResponse.<PlayerSeasonStatsResponse>builder()
                .data(stats)
                .message("Player season stats retrieved successfully")
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

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PlayerDto>> updatePlayer(
            @PathVariable Long id,
            @RequestBody UpdatePlayerRequest request) {
        PlayerDto updated = playerService.updatePlayer(id, request);
        return ResponseEntity.ok(ApiResponse.<PlayerDto>builder()
                .data(updated)
                .message("Player updated successfully")
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
