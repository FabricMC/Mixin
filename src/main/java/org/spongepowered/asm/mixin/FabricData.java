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

import java.lang.reflect.Field;
import java.util.Map;

import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.injection.selectors.ISelectorContext;
import org.spongepowered.asm.mixin.transformer.Config;

public final class FabricData {
	public static final String KEY_MOD_ID = "modId";

	private static final FabricData DEFAULT = new FabricData(false, 
			"(unknown)");

	private static final Field FIELD_FABRICDATA;

	static {
		try {
			FIELD_FABRICDATA = Class.forName("org.spongepowered.asm.mixin.transformer.MixinConfig").getDeclaredField("fabricData");
			FIELD_FABRICDATA.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	static void attach(Config config, Map<String, Object> dataMap) {
		FabricData data = new FabricData(true,
				(String) dataMap.getOrDefault(KEY_MOD_ID, DEFAULT.modId));

		try {
			FIELD_FABRICDATA.set(config.getConfig(), data);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	public static FabricData get(IMixinConfig config) {
		try {
			FabricData ret = (FabricData) FIELD_FABRICDATA.get(config);

			return ret != null ? ret : DEFAULT;
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static FabricData get(ISelectorContext context) {
		return get(context.getMixin().getMixin().getConfig());
	}

	public final boolean available;

	public final String modId;

	private FabricData(boolean available, 
			String modId) {
		this.available = available;
		this.modId = modId;
	}
}
