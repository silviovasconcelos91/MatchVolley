package vasconcelos.volleymatch.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vasconcelos.volleymatch.dto.common.ApiResponse;
import vasconcelos.volleymatch.dto.livesession.PublicLiveViewDto;
import vasconcelos.volleymatch.model.live.LiveSessionHeader;
import vasconcelos.volleymatch.service.LivePublicViewService;
import vasconcelos.volleymatch.service.LivePublicViewService.VersionedView;
import vasconcelos.volleymatch.service.LiveSessionCodeGenerator;

import java.util.Optional;

/**
 * Unauthenticated live view polled by the overlay page. Unknown and expired codes both return
 * the same 404 so that code existence is never revealed.
 */
@RestController
@RequestMapping("/api/v1/public/live")
@RequiredArgsConstructor
public class PublicLiveController {

    private static final String NOT_FOUND_MESSAGE = "Live not found";

    private final LivePublicViewService livePublicViewService;

    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<PublicLiveViewDto>> getPublicLive(
            @PathVariable String code,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        Optional<String> normalized = LiveSessionCodeGenerator.normalize(code);
        Optional<LiveSessionHeader> header = normalized.flatMap(livePublicViewService::findHeader);
        if (header.isEmpty()) {
            return notFound();
        }
        String etag = LivePublicViewService.etag(header.get());
        if (LivePublicViewService.matchesEtag(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(CacheControl.noCache())
                    .build();
        }

        Optional<VersionedView> view = livePublicViewService.findView(normalized.get());
        if (view.isEmpty()) {
            return notFound();
        }
        return ResponseEntity.ok()
                .eTag(view.get().etag())
                .cacheControl(CacheControl.noCache())
                .body(ApiResponse.<PublicLiveViewDto>builder()
                        .data(view.get().view())
                        .message("Live retrieved successfully")
                        .status(HttpStatus.OK.value())
                        .build());
    }

    private static ResponseEntity<ApiResponse<PublicLiveViewDto>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .cacheControl(CacheControl.noCache())
                .body(ApiResponse.<PublicLiveViewDto>builder()
                        .data(null)
                        .message(NOT_FOUND_MESSAGE)
                        .status(HttpStatus.NOT_FOUND.value())
                        .build());
    }
}
