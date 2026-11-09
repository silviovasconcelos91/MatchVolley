package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.model.live.LiveStatsSnapshotConverter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LiveStatsSnapshotConverterTest {

    private final LiveStatsSnapshotConverter converter = new LiveStatsSnapshotConverter();

    @Test
    void should_restore_identical_snapshot_when_round_tripped() {
        LiveStatsRequest snapshot = LiveSnapshotFixtures.snapshot(List.of(
                LiveSnapshotFixtures.set(1, "mine", List.of(
                        LiveSnapshotFixtures.attackPoint(1, 3L, 7, "Dupont"),
                        LiveSnapshotFixtures.opponentFault(2, 9),
                        LiveSnapshotFixtures.neutral(3, 3L, 7, "Dupont"))),
                LiveSnapshotFixtures.set(2, null, List.of(
                        LiveSnapshotFixtures.opponentPoint(1, 5)))));

        String json = converter.convertToDatabaseColumn(snapshot);

        assertThat(json).contains("\"teamId\"").contains("\"sets\"");
        assertThat(converter.convertToEntityAttribute(json)).isEqualTo(snapshot);
    }

    @Test
    void should_return_null_when_snapshot_is_null() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
