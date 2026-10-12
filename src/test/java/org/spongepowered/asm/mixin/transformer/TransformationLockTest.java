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
package org.spongepowered.asm.mixin.transformer;

import org.jetbrains.lincheck.Lincheck;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformationLockTest {
    @Test
    void testDeadlockFree() {
        Lincheck.runConcurrentTest(() -> {
            Scenario scenario = new Scenario();
            Thread t1 = thread(() -> {
                boolean transformed = scenario.withTransformationLock("Target 1", () -> {
                    scenario.simulateClassLoad("A1");
                    return true;
                }, false);
                assertTrue(transformed, "Target 1 not transformed");
            });
            Thread t2 = thread(() -> {
                boolean transformed = scenario.withTransformationLock("Target 2", () -> {
                    scenario.simulateClassLoad("A2");
                    return true;
                }, false);
                assertTrue(transformed, "Target 2 not transformed");
            });
            Thread t3 = thread(() -> scenario.simulateClassLoad("A1"));
            Thread t4 = thread(() -> scenario.simulateClassLoad("A2"));
            try {
                t1.join();
                t2.join();
                t3.join();
                t4.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Thread thread(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.start();
        return thread;
    }

    private static final class Scenario {
        private final ConcurrentMap<String, Object> clLocks = new ConcurrentHashMap<>();
        private final TransformationLock transformationLock = new TransformationLock(this::getSuperTypes);
        private final Set<String> loaded = Collections.newSetFromMap(new ConcurrentHashMap<>());

        public void withClLock(String name, Runnable block) {
            synchronized (clLocks.computeIfAbsent(name, k -> new Object())) {
                block.run();
            }
        }

        public <T> T withTransformationLock(String name, Supplier<T> block, T fallback) {
            return transformationLock.withLock(name, fallback, block);
        }

        public void simulateClassLoad(String name) {
            transformationLock.notifyClassLoadAttempt(name);
            withClLock(name, () -> {
                if (loaded.contains(name)) {
                    return;
                }
                withTransformationLock(name, () -> null, null);
                loaded.add(name);
                for (String superType : getSuperTypes(name)) {
                    simulateClassLoad(superType);
                }
            });
        }

        private List<String> getSuperTypes(String className) {
            switch (className) {
                case "A1":
                    return Collections.singletonList("B");
                case "A2":
                    return Arrays.asList("B", "D");
                case "B":
                    return Collections.singletonList("C");
                case "C":
                    return Collections.singletonList("D");
                case "D":
                    return Collections.singletonList("java.lang.Object");
                case "java.lang.Object":
                    return Collections.emptyList();
                default:
                    throw new IllegalArgumentException("Unknown class: " + className);
            }
        }
    }
}