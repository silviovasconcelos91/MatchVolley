package vasconcelos.silvio.volleymatch.dto.common;

import lombok.Builder;

@Builder
public record ApiResponse<T>(
        T data,
        String message,
        int status
) {}
