package vasconcelos.silvio.volleymatch.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vasconcelos.silvio.volleymatch.dto.common.ApiResponse;
import vasconcelos.silvio.volleymatch.dto.match.MatchDetailResponse;
import vasconcelos.silvio.volleymatch.dto.match.MatchStatRequest;
import vasconcelos.silvio.volleymatch.dto.match.MatchStatResponse;
import vasconcelos.silvio.volleymatch.service.MatchStatService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MatchStatController {

    private final MatchStatService matchStatService;

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
}
