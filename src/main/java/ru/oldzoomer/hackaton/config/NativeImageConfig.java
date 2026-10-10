package ru.oldzoomer.hackaton.config;

import org.jspecify.annotations.NonNull;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@ImportRuntimeHints(NativeImageConfig.XxHashHints.class)
@Configuration(proxyBeanMethods = false)
public class NativeImageConfig {

    static class XxHashHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(@NonNull RuntimeHints hints, ClassLoader classLoader) {
            String[] lz4Classes = {
                    "net.jpountz.xxhash.XXHash32JavaSafe",
                    "net.jpountz.xxhash.StreamingXXHash32JavaSafe$Factory",
                    "net.jpountz.xxhash.XXHash64JavaSafe",
                    "net.jpountz.xxhash.StreamingXXHash64JavaSafe$Factory",
                    "net.jpountz.xxhash.XXHashFactory",
                    "net.jpountz.lz4.LZ4JavaSafeCompressor",
                    "net.jpountz.lz4.LZ4JavaSafeFastDecompressor",
                    "net.jpountz.lz4.LZ4JavaSafeSafeDecompressor",
                    "net.jpountz.lz4.LZ4Factory"
            };

            for (String className : lz4Classes) {
                hints.reflection().registerType(TypeReference.of(className),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS,
                        MemberCategory.ACCESS_PUBLIC_FIELDS
                );
            }

            hints.resources().registerPattern("net/jpountz/.*");
        }
    }
}
