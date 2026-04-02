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

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldNode;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.transformer.MixinInfo.MixinClassNode;
import org.spongepowered.asm.mixin.transformer.throwables.InvalidMixinException;
import org.spongepowered.asm.util.Annotations;
import org.spongepowered.asm.util.Bytecode;
import org.spongepowered.asm.util.Constants;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

class MixinPreProcessorEnumExtension extends MixinPreProcessorStandard {
    private static final List<Class<? extends Annotation>> DISALLOWED_ANNOTATIONS_ON_CONSTANTS =
            Arrays.asList(Shadow.class, Unique.class);

    MixinPreProcessorEnumExtension(MixinInfo mixin, MixinClassNode classNode) {
        super(mixin, classNode);
    }

    @Override
    protected void prepareShadow(MixinInfo.MixinMethodNode mixinMethod, ClassInfo.Method method) {
        if (Constants.CTOR.equals(mixinMethod.name) && !Bytecode.hasFlag(mixinMethod, Opcodes.ACC_SYNTHETIC)) {
            // In enum extensions, constructors are always implicitly @Shadow s (except synthetic ones which will be
            // merged and so don't need to match anything).
            Annotations.setVisible(mixinMethod, Shadow.class);
        }
        super.prepareShadow(mixinMethod, method);
    }

    @Override
    protected boolean validateField(MixinTargetContext context, FieldNode field, AnnotationNode shadow) {
        if (Bytecode.isEnumConstant(field, classNode)) {
            for (Class<? extends Annotation> annotation : DISALLOWED_ANNOTATIONS_ON_CONSTANTS) {
                if (Annotations.getVisible(field, annotation) != null) {
                    throw new InvalidMixinException(context, String.format("Enum constant %s in %s has @%s annotation. This is not allowed.",
                            field.name, context, annotation.getSimpleName()));
                }
            }
            // Public static fields normally aren't allowed, but enum values are excused
            return true;
        }
        return super.validateField(context, field, shadow);
    }

    @Override
    protected boolean validateMethod(MixinTargetContext context, MixinInfo.MixinMethodNode mixinMethod) {
        String mixinClassDesc = 'L' + this.mixin.getClassRef() + ';';
        if (mixinMethod.name.equals("values") && mixinMethod.desc.equals("()[" + mixinClassDesc)) {
            // Skip the values() method
            return false;
        }
        if (mixinMethod.name.equals("valueOf") && mixinMethod.desc.equals("(Ljava/lang/String;)" + mixinClassDesc)) {
            // Skip the valueOf(String) method
            return false;
        }
        return super.validateMethod(context, mixinMethod);
    }
}
