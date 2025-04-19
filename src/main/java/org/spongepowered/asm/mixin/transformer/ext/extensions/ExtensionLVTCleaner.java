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
package org.spongepowered.asm.mixin.transformer.ext.extensions;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ILazyClassNode;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;
import org.spongepowered.asm.util.Locals;

import java.util.Iterator;

/**
 * Strips synthetic local variables from the LVT after exporting to avoid debuggers becoming confused by them.
 */
public class ExtensionLVTCleaner implements IExtension {

    /* (non-Javadoc)
     * @see org.spongepowered.asm.mixin.transformer.ext.IExtension#checkActive(
     *      org.spongepowered.asm.mixin.MixinEnvironment)
     */
    @Override
    public boolean checkActive(MixinEnvironment environment) {
        return true;
    }

    /* (non-Javadoc)
     * @see org.spongepowered.asm.mixin.transformer.IMixinTransformerModule
     *     #preApply(org.spongepowered.asm.mixin.transformer.TargetClassContext)
     */
    @Override
    public void preApply(ITargetClassContext context) {
    }

    /* (non-Javadoc)
     * @see org.spongepowered.asm.mixin.transformer.IMixinTransformerModule
     *    #postApply(org.spongepowered.asm.mixin.transformer.TargetClassContext)
     */
    @Override
    public void postApply(ITargetClassContext context) {
    }

    /* (non-Javadoc)
     * @see org.spongepowered.asm.mixin.transformer.ext.IExtension
     *      #export(org.spongepowered.asm.mixin.MixinEnvironment,
     *      java.lang.String, boolean, org.spongepowered.asm.mixin.transformer.ILazyClassNode)
     */
    @Override
    public void export(MixinEnvironment env, String name, boolean force, ILazyClassNode classNode) {
        if (!classNode.hasLoaded()) return; //No changes can have happened to the LVT if it's not loaded
        for (MethodNode methodNode : classNode.get().methods) {
            if (methodNode.localVariables != null) {
                for (Iterator<LocalVariableNode> it = methodNode.localVariables.iterator(); it.hasNext(); ) {
                    if (it.next() instanceof Locals.SyntheticLocalVariableNode) {
                        it.remove();
                    }
                }
            }
        }
    }

}
