package org.spongepowered.asm.mixin.transformer;

import java.util.function.Supplier;

import org.objectweb.asm.tree.ClassNode;

public final class LazyClassNode implements ILazyClassNode {
    private final Supplier<ClassNode> factory;
    private ClassNode node;

    public static ILazyClassNode of(Supplier<ClassNode> factory) {
        return new LazyClassNode(factory, null);
    }

    public static ILazyClassNode of(ClassNode node) {
        return new LazyClassNode(null, node);
    }

    private LazyClassNode(Supplier<ClassNode> factory, ClassNode node) {
        this.factory = factory;
        this.node = node;
    }

    @Override
    public boolean hasLoaded() {
        return node != null;
    }

    @Override
    public ClassNode get() {
        if (!hasLoaded()) {
            node = factory.get();
        }

        return node;
    }
}