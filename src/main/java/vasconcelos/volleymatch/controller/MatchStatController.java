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
import vasconcelos.volleymatch.dto.live.LiveStatsSavedResponse;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.dto.live.LiveStatsResponse;
import vasconcelos.volleymatch.dto.match.MatchDetailResponse;
import vasconcelos.volleymatch.dto.match.MatchStatRequest;
import vasconcelos.volleymatch.dto.match.MatchStatResponse;
import vasconcelos.volleymatch.dto.live.LiveMatchAnalysisResponse;
import vasconcelos.volleymatch.service.LiveStatsAnalysisService;
import vasconcelos.volleymatch.service.LiveStatsService;
import vasconcelos.volleymatch.service.MatchStatService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MatchStatController {

    private final MatchStatService matchStatService;
    private final LiveStatsService liveStatsService;
    private final LiveStatsAnalysisService liveStatsAnalysisService;

    @PostMapping("/match-stats")
    public ResponseEntity<ApiResponse<MatchStatResponse>> saveMatchStat(
            @RequestBody MatchStatRequest request) {

        MatchStatResponse data = matchStatService.saveMatchStat(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.<MatchStatResponse>builder()
                        .data(data)
                        .message("Match stats saved successfully")
                        .status(HttpStatus.CREATED.value())
                        .build());
    }

    @GetMapping("/match-stats/{matchId}")
    public ResponseEntity<ApiResponse<MatchDetailResponse>> getMatchStat(
            @PathVariable String matchId) {

        MatchDetailResponse data = matchStatService.getMatchStat(matchId);

        return ResponseEntity.ok(ApiResponse.<MatchDetailResponse>builder()
                .data(data)
                .message("Match stats retrieved successfully")
                .status(HttpStatus.OK.value())
                .build());
    }

    @PostMapping("/matches/{matchId}/live-stats")
    public ResponseEntity<ApiResponse<LiveStatsSavedResponse>> saveLiveStats(
            @PathVariable String matchId,
            @RequestBody LiveStatsRequest request) {

        if (!matchId.equals(request.matchId())) {
            throw new IllegalArgumentException("Path matchId and body matchId must match");
        }
        LiveStatsSavedResponse data = liveStatsService.saveLiveStats(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.<LiveStatsSavedResponse>builder()
                        .data(data)
                        .message("Live stats saved successfully")
                        .status(HttpStatus.CREATED.value())
                        .build());
    }

    @GetMapping("/matches/{matchId}/live-stats")
    public ResponseEntity<ApiResponse<LiveStatsResponse>> getLiveStats(
            @PathVariable String matchId) {

        LiveStatsResponse data = liveStatsService.getLiveStats(matchId);

        return ResponseEntity.ok(ApiResponse.<LiveStatsResponse>builder()
                .data(data)
                .message("Live stats retrieved successfully")
                .status(HttpStatus.OK.value())
                .build());
    }

    @GetMapping("/matches/{matchId}/live-stats/analysis")
    public ResponseEntity<ApiResponse<LiveMatchAnalysisResponse>> getLiveStatsAnalysis(
            @PathVariable String matchId) {

        LiveMatchAnalysisResponse data = liveStatsAnalysisService.getMatchAnalysis(matchId);

        return ResponseEntity.ok(ApiResponse.<LiveMatchAnalysisResponse>builder()
                .data(data)
                .message("Live stats analysis retrieved successfully")
                .status(HttpStatus.OK.value())
                .build());
    }
}
