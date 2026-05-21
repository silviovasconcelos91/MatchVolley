package vasconcelos.volleymatch.config;

import java.util.UUID;
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
                    .registerType(UUID[].class, MemberCategory.values());
        }
    }
}