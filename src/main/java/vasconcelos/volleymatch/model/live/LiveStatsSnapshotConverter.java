package vasconcelos.volleymatch.model.live;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.json.JsonMapper;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;

/**
 * Serializes the live snapshot with Jackson 3 (same engine as Spring MVC) instead of Hibernate's
 * own JSON FormatMapper, which fails in the native image ("Could not serialize object of java type").
 * Reflection hints for {@link LiveStatsRequest} are registered in {@code NativeConfig}.
 */
@Converter
public class LiveStatsSnapshotConverter implements AttributeConverter<LiveStatsRequest, String> {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Override
    public String convertToDatabaseColumn(LiveStatsRequest snapshot) {
        return snapshot == null ? null : JSON_MAPPER.writeValueAsString(snapshot);
    }

    @Override
    public LiveStatsRequest convertToEntityAttribute(String json) {
        return json == null ? null : JSON_MAPPER.readValue(json, LiveStatsRequest.class);
    }
}
