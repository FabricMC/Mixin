/*
 * This file is part of Mixin, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.spongepowered.asm.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.injection.selectors.ISelectorContext;

public final class FabricUtil {
    public static final String KEY_MOD_ID = "fabric-modId";
    public static final String KEY_COMPATIBILITY = "fabric-compat";

    // fabric mixin version compatibility boundaries, (major * 1000 + minor) * 1000 + patch
    public static final int COMPATIBILITY_0_9_2 = 9002; // 0.9.2+mixin.0.8.2 incompatible local variable handling
    public static final int COMPATIBILITY_0_10_0 = 10000; // 0.10.0+mixin.0.8.4
    public static final int COMPATIBILITY_LATEST = COMPATIBILITY_0_10_0;

    public static String getModId(IMixinConfig config) {
        return getDecoration(config, KEY_MOD_ID, "(unknown)");
    }
    
    public static String getModId(ISelectorContext context) {
        return getDecoration(getConfig(context), KEY_MOD_ID, "(unknown)");
    }

    public static int getCompatibility(ISelectorContext context) {
        return getDecoration(getConfig(context), KEY_COMPATIBILITY, COMPATIBILITY_LATEST);
    }

    private static IMixinConfig getConfig(ISelectorContext context) {
        return context.getMixin().getMixin().getConfig();
    }

    private static <T> T getDecoration(IMixinConfig config, String key, T defaultValue) {
        if (config.hasDecoration(key)) {
            return config.getDecoration(key);
        } else {
            return defaultValue;
        }
    }
}
