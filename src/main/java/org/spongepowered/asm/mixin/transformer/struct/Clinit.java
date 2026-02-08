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
package org.spongepowered.asm.mixin.transformer.struct;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.util.Bytecode;

import java.util.ListIterator;
import java.util.Map;

public class Clinit {
    private final MethodNode clinit;
    private final AbstractInsnNode finalReturn;

    public Clinit(MethodNode clinit, AbstractInsnNode finalReturn) {
        this.clinit = clinit;
        this.finalReturn = finalReturn;
    }

    public void append(MethodNode mixinClinit) {
        prepareClinit(mixinClinit, null);
        Map<LabelNode, LabelNode> labels = Bytecode.cloneLabels(mixinClinit.instructions);
        for (AbstractInsnNode insn : mixinClinit.instructions) {
            if (insn.getOpcode() == Opcodes.RETURN) {
                continue;
            }
            this.clinit.instructions.insertBefore(this.finalReturn, insn.clone(labels));
        }
        this.clinit.maxLocals = Math.max(this.clinit.maxLocals, mixinClinit.maxLocals);
        this.clinit.maxStack = Math.max(this.clinit.maxStack, mixinClinit.maxStack);
        for (TryCatchBlockNode tryCatch : mixinClinit.tryCatchBlocks) {
            this.clinit.tryCatchBlocks.add(new TryCatchBlockNode(labels.get(tryCatch.start), labels.get(tryCatch.end), labels.get(tryCatch.handler), tryCatch.type));
        }
        for (LocalVariableNode local : mixinClinit.localVariables) {
            this.clinit.localVariables.add(new LocalVariableNode(local.name, local.desc, local.signature, labels.get(local.start), labels.get(local.end), local.index));
        }
    }

    public static Clinit prepare(Target clinit) {
        return new Clinit(clinit.method, Clinit.prepareClinit(clinit.method, clinit));
    }

    /**
     * Rewrites RETURN instructions to instead GOTO a final RETURN instruction, and returns said instruction.
     */
    private static AbstractInsnNode prepareClinit(MethodNode clinit, Target target) {
        LabelNode endLabel = new LabelNode();
        AbstractInsnNode existingFinalReturn = null;
        for (ListIterator<AbstractInsnNode> iter = clinit.instructions.iterator(); iter.hasNext(); ) {
            AbstractInsnNode insn = iter.next();

            if (insn.getOpcode() == Opcodes.RETURN) {
                if (insn.getNext() == null) {
                    existingFinalReturn = insn;
                    break;
                }
                AbstractInsnNode newInsn = new JumpInsnNode(Opcodes.GOTO, endLabel);
                iter.set(newInsn);

                if (target != null) {
                    InjectionNodes.InjectionNode injectionNode = target.getInjectionNode(insn);
                    if (injectionNode != null) {
                        // Ensure our rewriting doesn't break injectors which have targeted these RETURNs.
                        // Since void RETURNs are value-less anyway, they are only targeted by injectors which accept 
                        // all instructions, so the replacement is sound.
                        injectionNode.replace(newInsn);
                    }
                }
            }
        }
        if (existingFinalReturn != null) {
            // Keep the existing final return if possible to simplify things.
            clinit.instructions.insertBefore(existingFinalReturn, endLabel);
            return existingFinalReturn;
        }
        clinit.instructions.add(endLabel);
        InsnNode finalReturn = new InsnNode(Opcodes.RETURN);
        clinit.instructions.add(finalReturn);
        return finalReturn;
    }
}
