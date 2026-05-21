package vasconcelos.volleymatch.dto.match;

import java.time.Instant;

public record MetaDto(
        Instant clientGeneratedAt,
        String appVersion
) {}
