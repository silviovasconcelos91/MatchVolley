package vasconcelos.volleymatch.config;

import java.util.UUID;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.model.live.LiveSessionHeader;
import vasconcelos.volleymatch.model.live.LiveStatsSnapshotConverter;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(NativeConfig.AppRuntimeHints.class)
public class NativeConfig {

    static class AppRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection()
                    .registerType(UUID.class, MemberCategory.values())
                    .registerType(UUID[].class, MemberCategory.values())
                    // JPQL constructor expression in LiveSessionRepository#findHeaderByCode
                    .registerType(LiveSessionHeader.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
                    // JPA converter instantiated by Hibernate for LiveSession#snapshot
                    .registerType(LiveStatsSnapshotConverter.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
            // Snapshot JSON (de)serialization: LiveStatsRequest and its nested records
            new BindingReflectionHintsRegistrar().registerReflectionHints(hints.reflection(), LiveStatsRequest.class);
        }
    }
}