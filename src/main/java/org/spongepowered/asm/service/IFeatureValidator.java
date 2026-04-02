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

package org.spongepowered.asm.service;

import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.mixin.transformer.throwables.InvalidMixinException;

/**
 * Feature validators are responsible for deciding if certain features are allowed in given contexts.
 */
public interface IFeatureValidator {
    /**
     * Decides whether the given mixin can enum-extend the given target class.
     * If not, should throw an {@link InvalidMixinException} with an informative error as to why this is not allowed.
     * @param mixin the enum extension Mixin
     * @param targetClass the target class the Mixin is trying to extend
     */
    public abstract void validateEnumExtension(IMixinInfo mixin, ClassInfo targetClass) throws InvalidMixinException;

    /**
     * Allows all features.
     */
    public static final IFeatureValidator ALLOW_ALL = new IFeatureValidator() {
        @Override
        public void validateEnumExtension(IMixinInfo mixin, ClassInfo targetClass) throws InvalidMixinException {
        }
    };
}
