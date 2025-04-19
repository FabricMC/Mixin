package org.spongepowered.asm.mixin.transformer;

import org.objectweb.asm.tree.ClassNode;

/**
 * A provider of a {@link ClassNode}, which allows only creating it if necessary
 */
public interface ILazyClassNode {
    /**
     * Check if the {@link ClassNode} is immediately available
     *
     * @return Whether {@link #get()} can return the {@link ClassNode} without loading it first
     */
    boolean hasLoaded();

    /**
     * Get the {@link ClassNode} that this provider is for
     *
     * @return The {@link ClassNode} this provider is for
     */
    ClassNode get();
}