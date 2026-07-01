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
package org.spongepowered.asm.mixin.extensibility;

import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Set;

/**
 * <p>An interface providing default implementations for {@link IMixinConfigPlugin} for convenience.</p>
 *
 * <p>Most implementations of {@link IMixinConfigPlugin} only need to override a few methods,
 * and may accidentally provide a return value that doesn't represent the expected "default" from Mixin for the methods
 * they don't need to interact with. By using this interface, users only have to worry about the methods they want to override.</p>
 *
 * @see IMixinConfigPlugin
 */
public interface IDefaultedMixinConfigPlugin extends IMixinConfigPlugin {

    /**
     * {@inheritDoc}
     * @param mixinPackage {@inheritDoc}
     */
    @Override
    default void onLoad(String mixinPackage) {
    }

    /**
     * {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    default String getRefMapperConfig() {
        return null;
    }

    /**
     * {@inheritDoc}
     * @param targetClassName {@inheritDoc}
     * @param mixinClassName {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    default boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    /**
     * {@inheritDoc}
     * @param myTargets {@inheritDoc}
     * @param otherTargets {@inheritDoc}
     */
    @Override
    default void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    /**
     * {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    default List<String> getMixins() {
        return null;
    }

    /**
     * {@inheritDoc}
     * @param targetClassName {@inheritDoc}
     * @param targetClass {@inheritDoc}
     * @param mixinClassName {@inheritDoc}
     * @param mixinInfo {@inheritDoc}
     */
    @Override
    default void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    /**
     * {@inheritDoc}
     * @param targetClassName {@inheritDoc}
     * @param targetClass {@inheritDoc}
     * @param mixinClassName {@inheritDoc}
     * @param mixinInfo {@inheritDoc}
     */
    @Override
    default void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
