package com.browserapi.component.model;

import java.util.Set;

/**
 * Configuration options for generating iframe embed codes.
 */
public record EmbedOptions(
        EmbedType type,
        Integer width,
        Integer height,
        boolean sandbox,
        Set<SandboxFlag> sandboxFlags
) {
    /**
     * Default embed options with safe sandbox configuration.
     */
    public static EmbedOptions defaults() {
        return new EmbedOptions(
                EmbedType.RESPONSIVE,
                800,
                600,
                true,
                Set.of(SandboxFlag.ALLOW_SCRIPTS, SandboxFlag.ALLOW_SAME_ORIGIN)
        );
    }

    /**
     * Fixed size embed with default dimensions.
     */
    public static EmbedOptions fixed() {
        return new EmbedOptions(
                EmbedType.FIXED,
                800,
                600,
                true,
                Set.of(SandboxFlag.ALLOW_SCRIPTS, SandboxFlag.ALLOW_SAME_ORIGIN)
        );
    }

    /**
     * Responsive embed that adapts to container.
     */
    public static EmbedOptions responsive() {
        return new EmbedOptions(
                EmbedType.RESPONSIVE,
                null,
                null,
                true,
                Set.of(SandboxFlag.ALLOW_SCRIPTS, SandboxFlag.ALLOW_SAME_ORIGIN)
        );
    }

    /**
     * Minimal embed with no styling or sandbox.
     */
    public static EmbedOptions minimal() {
        return new EmbedOptions(
                EmbedType.MINIMAL,
                null,
                null,
                false,
                Set.of()
        );
    }

    /**
     * Custom sized embed.
     */
    public static EmbedOptions custom(int width, int height) {
        return new EmbedOptions(
                EmbedType.FIXED,
                width,
                height,
                true,
                Set.of(SandboxFlag.ALLOW_SCRIPTS, SandboxFlag.ALLOW_SAME_ORIGIN)
        );
    }
}
