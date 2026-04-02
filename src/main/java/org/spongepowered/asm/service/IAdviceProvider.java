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

/**
 * Advice providers are responsible for providing suggestions to solve service-specific problems.
 */
public interface IAdviceProvider {
    /**
     * Advise the user on how to increase their compatibility version.
     * @param requiredCompatibility the required compatibility version
     * @param requiredCompatibilityString the required compatibility version as a string, in case it is useful in messages
     */
    public abstract String higherCompatibilityNeeded(int requiredCompatibility, String requiredCompatibilityString);

    /**
     * Returns generic advice for all situations. You should prefer implementing specific advice for your service.
     */
    public static final IAdviceProvider GENERIC = new IAdviceProvider() {
        @Override
        public String higherCompatibilityNeeded(int requiredCompatibility, String requiredCompatibilityString) {
            return "Increase your compatibility version to at least " + requiredCompatibilityString;
        }
    };
}
