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

import java.lang.reflect.Modifier;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.MixinEnvironment.CompatibilityLevel;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.transformer.ClassInfo.Method;
import org.spongepowered.asm.mixin.transformer.MixinInfo.MixinClassNode;
import org.spongepowered.asm.mixin.transformer.MixinInfo.MixinMethodNode;
import org.spongepowered.asm.mixin.transformer.throwables.InvalidInterfaceMixinException;
import org.spongepowered.asm.mixin.transformer.throwables.InvalidMixinException;
import org.spongepowered.asm.util.Annotations;
import org.spongepowered.asm.util.Bytecode;
import org.spongepowered.asm.util.Constants;
import org.spongepowered.asm.util.LanguageFeatures;

/**
 * Bytecode preprocessor for interface mixins, simply performs some additional
 * verification for things which are unsupported in interfaces
 */
class MixinPreProcessorInterface extends MixinPreProcessorStandard {
    
    /**
     * Ctor
     * 
     * @param mixin Mixin info
     * @param classNode Mixin classnode
     */
    MixinPreProcessorInterface(MixinInfo mixin, MixinClassNode classNode) {
        super(mixin, classNode);
    }

    /* (non-Javadoc)
     * @see org.spongepowered.asm.mixin.transformer.MixinPreProcessor
     *      #prepareMethod(org.objectweb.asm.tree.MethodNode,
     *      org.spongepowered.asm.mixin.transformer.ClassInfo.Method)
     */
    @Override
    protected void prepareMethod(MixinMethodNode mixinMethod, Method method) {
        // Userland interfaces should not have non-public methods except for lambda bodies
        if (!Bytecode.hasFlag(mixinMethod, Opcodes.ACC_PUBLIC)) {
            if (Bytecode.hasFlag(mixinMethod, Opcodes.ACC_SYNTHETIC)) {
                CompatibilityLevel requiredLevel = CompatibilityLevel.requiredFor(LanguageFeatures.PRIVATE_SYNTHETIC_METHODS_IN_INTERFACES);
                if (MixinEnvironment.getCompatibilityLevel().isLessThan(requiredLevel)) {
                    throw new InvalidInterfaceMixinException(this.mixin, String.format(
                            "Interface mixin contains a synthetic private method but compatibility level %s is required! Found %s in %s",
                            requiredLevel, method, this.mixin));
                }
            } else if (Constants.CLINIT.equals(mixinMethod.name) && "()V".equals(mixinMethod.desc)) {
                return; //In order to shadow fields they must have a value set, this may result in a static initialiser being included
            } else if (!Bytecode.hasFlag(mixinMethod, Opcodes.ACC_PRIVATE) || !MixinEnvironment.getCompatibilityLevel().supports(LanguageFeatures.PRIVATE_METHODS_IN_INTERFACES)) {
                //On versions that support it private methods are also allowed
                throw new InvalidInterfaceMixinException(this.mixin, "Interface mixin contains a non-public method! Found " + method + " in "
                        + this.mixin);
            }
        }
        
        super.prepareMethod(mixinMethod, method);
    }
    
    /* (non-Javadoc)
     * @see org.spongepowered.asm.mixin.transformer.MixinPreProcessor
     *      #validateField(
     *      org.spongepowered.asm.mixin.transformer.MixinTargetContext,
     *      org.objectweb.asm.tree.FieldNode,
     *      org.objectweb.asm.tree.AnnotationNode)
     */
    @Override
    protected boolean validateField(MixinTargetContext context, FieldNode field, AnnotationNode shadow) {
        //All fields in interfaces are public, static, final constants (or the JVM throws a ClassFormatError)
        if (!Bytecode.isStatic(field) || !Bytecode.hasFlag(field, Opcodes.ACC_PUBLIC) || !Bytecode.hasFlag(field, Opcodes.ACC_FINAL)) {
            throw new InvalidInterfaceMixinException(this.mixin, String.format("Interface mixin contains an illegal field! Found %s %s in %s",
                    Modifier.toString(field.access), field.name, this.mixin));
        }
        
        //Whilst we could support adding constants, they'd always be public so there's little benefit to allowing it
        if (shadow == null) {
            throw new InvalidInterfaceMixinException(this.mixin, String.format("Interface mixin %s contains a non-shadow field: %s",
                    this.mixin, field.name));
        }

        //Making a field non-final will result in verification crashes, so @Mutable is always a mistake
        if (Annotations.getVisible(field, Mutable.class) != null) {
        	throw new InvalidInterfaceMixinException(this.mixin, String.format("@Shadow field %s.%s is marked as mutable. This is not allowed.",
                    this.mixin, field.name));
        }

        //Shadow fields can't have prefixes, it's meaningless for them anyway
        String prefix = Annotations.<String>getValue(shadow, "prefix", Shadow.class);
        if (field.name.startsWith(prefix)) {
            throw new InvalidMixinException(context, String.format("@Shadow field %s.%s has a shadow prefix. This is not allowed.",
                    context, field.name));
        }

        //Imaginary super fields are only supported for classes
        if (Constants.IMAGINARY_SUPER.equals(field.name)) {
            throw new InvalidInterfaceMixinException(this.mixin, String.format("Interface mixin %s contains an imaginary super. This is not allowed",
                    this.mixin));
        }

        return true;
    }
    
}
