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

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Custom lock to avoid deadlocks when re-entrantly loaded classes are also loaded on other threads.
 * Stops waiting for the transformation lock if we realise the class we want to transform has been loaded re=entrantly
 * on another thread.
 */
class TransformationLock {
    // Bookkeeping lock, never held during external operations
    private final ReentrantLock lock = new ReentrantLock();
    // Populated when a thread holds the CL lock for a given class but has to wait for the transformation lock
    private final Map<String, Condition> waitTokens = new HashMap<>();
    // Set of classes we can safely skip transformations for
    private final Set<String> skipTransformationFor = new HashSet<>();
    // Used to determine the class loads that will follow while still holding the CL lock for each class
    private final Function<String, List<String>> getSuperTypes;
    // Current lock holder
    private Thread holder = null;
    // Number of locks held (this lock is reentrant)
    private int lockCount = 0;

    public TransformationLock(Function<String, List<String>> getSuperTypes) {
        this.getSuperTypes = getSuperTypes;
    }

    /**
     * Performs the given operation while holding the transformation lock.
     * If the transformation is cancelled because the target is loaded re-entrantly, returns the fallback.
     */
    public <T> T withLock(String className, T fallback, Supplier<T> operation) {
        boolean success = acquireTransformationLock(className);
        if (!success) {
            // Cancelled, no need to do any transformation
            return fallback;
        }
        try {
            return operation.get();
        } finally {
            releaseTransformationLock();
        }
    }

    /**
     * Notifies an attempt to load a class (must be called *before* acquiring that class's CL lock).
     */
    public void notifyClassLoadAttempt(String className) {
        lock.lock();
        try {
            if (holder == Thread.currentThread()) {
                // Re-entrant load, therefore the given class and its supertypes cannot have any mixins applied to them
                skipTransformationRecursively(className);
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean acquireTransformationLock(String className) {
        lock.lock();
        try {
            if (holder == null || holder == Thread.currentThread()) {
                // Uncontended fast path
                holder = Thread.currentThread();
                lockCount++;
                return true;
            }
            Condition waitToken = lock.newCondition();
            waitTokens.put(className, waitToken);
            try {
                while (true) {
                    if (skipTransformationFor.contains(className)) {
                        // Skip
                        return false;
                    }
                    try {
                        waitToken.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting for transformation lock", e);
                    }
                    if (holder == null) {
                        // We can acquire the lock
                        holder = Thread.currentThread();
                        lockCount++;
                        return true;
                    }
                    // Got raced, try again
                }
            } finally {
                // Don't need this anymore
                waitTokens.remove(className);
            }
        } finally {
            lock.unlock();
        }
    }

    private void releaseTransformationLock() {
        lock.lock();
        try {
            if (--lockCount == 0) {
                holder = null;
                wakeUpWaiter();
            }
        } finally {
            lock.unlock();
        }
    }

    private void wakeUpWaiter() {
        // Only need to wake up 1 waiter when releasing the lock, because the waiter we wake up will always finish
        // its transformation and then wake up 1 more waiter, etc
        for (Condition waitToken : waitTokens.values()) {
            waitToken.signal();
            return;
        }
    }

    private void skipTransformationRecursively(String className) {
        // If we've seen this before no need to calculate the supertypes again
        if (skipTransformation(className)) {
            List<String> superTypes = getSuperTypes.apply(className);
            if (superTypes != null) {
                superTypes.forEach(this::skipTransformationRecursively);
            }
        }
    }

    private boolean skipTransformation(String className) {
        boolean isNew = skipTransformationFor.add(className);
        if (isNew) {
            // Cancel any thread waiting to apply mixins
            Condition waitToken = waitTokens.get(className);
            if (waitToken != null) {
                waitToken.signal();
            }
        }
        return isNew;
    }
}
