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
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.transformer.throwables.InvalidMixinException;

final class EnumExtensionUtils {
    private EnumExtensionUtils() {}

    public static void checkForGotchas(MixinInfo mixin, ClassNode classNode) throws InvalidMixinException {
        checkForOrdinalSwitch(mixin, classNode);
    }

    private static void checkForOrdinalSwitch(MixinInfo mixin, ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (isOrdinalCall(insn, mixin) && isSwitch(insn.getNext())) {
                    Integer line = findLineNumber(insn);
                    throw new InvalidMixinException(
                            mixin,
                            String.format(
                                    "`ordinal` switch on enum extension type is not supported but was found on line %s."
                                            + " Instead, switch on the target enum, e.g. `switch ((TargetEnum) (Object) ...)`",
                                    line
                            )
                    );
                }
            }
        }
    }

    private static boolean isOrdinalCall(AbstractInsnNode insn, MixinInfo mixin) {
        if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) {
            return false;
        }
        MethodInsnNode call = (MethodInsnNode) insn;
        return call.owner.equals(mixin.getClassRef()) && call.name.equals("ordinal") && call.desc.equals("()I");
    }

    private static boolean isSwitch(AbstractInsnNode insn) {
        return insn.getOpcode() == Opcodes.TABLESWITCH || insn.getOpcode() == Opcodes.LOOKUPSWITCH;
    }

    private static Integer findLineNumber(AbstractInsnNode insn) {
        do {
            if (insn instanceof LineNumberNode) {
                return ((LineNumberNode) insn).line;
            }
            insn = insn.getPrevious();
        } while (insn != null);

        return null;
    }
}
