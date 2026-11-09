package vasconcelos.volleymatch.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vasconcelos.volleymatch.dto.common.ApiResponse;
import vasconcelos.volleymatch.dto.livesession.CreateLiveSessionRequest;
import vasconcelos.volleymatch.dto.livesession.LiveSessionDto;
import vasconcelos.volleymatch.dto.livesession.LiveSnapshotRequest;
import vasconcelos.volleymatch.dto.livesession.LiveSnapshotResponse;
import vasconcelos.volleymatch.dto.livesession.UpdateLiveSessionRequest;
import vasconcelos.volleymatch.service.LiveSessionService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/live-sessions")
@RequiredArgsConstructor
public class LiveSessionController {

    private final LiveSessionService liveSessionService;

    @PostMapping
    public ResponseEntity<ApiResponse<LiveSessionDto>> createLiveSession(@RequestBody CreateLiveSessionRequest request) {
        LiveSessionDto data = liveSessionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<LiveSessionDto>builder()
                        .data(data)
                        .message("Live session created successfully")
                        .status(HttpStatus.CREATED.value())
                        .build());
    }

    @PutMapping("/{sessionId}/snapshot")
    public ResponseEntity<ApiResponse<LiveSnapshotResponse>> updateLiveSnapshot(
            @PathVariable UUID sessionId,
            @RequestBody LiveSnapshotRequest request) {
        LiveSnapshotResponse data = liveSessionService.updateSnapshot(sessionId, request);
        return ResponseEntity.ok(ApiResponse.<LiveSnapshotResponse>builder()
                .data(data)
                .message(data.applied() ? "Snapshot applied" : "Snapshot ignored (stale revision)")
                .status(HttpStatus.OK.value())
                .build());
    }

    @PostMapping("/{sessionId}:end")
    public ResponseEntity<ApiResponse<LiveSessionDto>> endLiveSession(@PathVariable UUID sessionId) {
        LiveSessionDto data = liveSessionService.end(sessionId);
        return ResponseEntity.ok(ApiResponse.<LiveSessionDto>builder()
                .data(data)
                .message("Live session ended")
                .status(HttpStatus.OK.value())
                .build());
    }

    @PatchMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<LiveSessionDto>> updateLiveSession(
            @PathVariable UUID sessionId,
            @RequestBody UpdateLiveSessionRequest request) {
        LiveSessionDto data = liveSessionService.update(sessionId, request);
        return ResponseEntity.ok(ApiResponse.<LiveSessionDto>builder()
                .data(data)
                .message("Live session updated")
                .status(HttpStatus.OK.value())
                .build());
    }
}
