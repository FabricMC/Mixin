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
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.transformer.throwables.InvalidMixinException;
import org.spongepowered.asm.util.Bytecode;
import org.spongepowered.asm.util.Constants;

import java.util.*;
import java.util.stream.Collectors;

final class EnumInfo implements Comparable<EnumInfo> {
    // Attached by build-time frameworks like class-tweaker, we remove these entries in favour of our own
    private static final String STUB_ENUM_CONSTANT_ATTRIBUTE = "org.spongepowered.asm.mixin.StubEnumConstant";

    private final ClassContext enumClass;
    private final ClassContext targetClass;
    private final String description;
    private final List<FieldNode> selfTypedFields;
    private final List<FieldNode> constants;
    private final Set<String> constantNames;
    private final FieldNode valuesField;
    private final MethodNode clinit;
    private final FieldInsnNode valuesAssignment;
    private final String tieBreakString;

    private EnumInfo(ClassContext enumClass, ClassContext targetClass, String description) throws AssumptionViolatedException {
        this.enumClass = enumClass;
        this.targetClass = targetClass;
        this.description = description;
        this.clinit = findClinit();
        this.selfTypedFields = findSelfTypedFields();
        this.constants = findEnumConstants();
        this.constantNames = this.constants.stream().map(it -> it.name).collect(Collectors.toSet());
        this.valuesField = findValuesField();
        this.valuesAssignment = findValuesAssignment();
        this.tieBreakString = this.constants.isEmpty() ? "" : this.constants.get(0).name;
    }

    public static EnumInfo forTarget(TargetClassContext target) throws AssumptionViolatedException {
        return new EnumInfo(target, target, "target class");
    }

    public static EnumInfo forMixin(MixinTargetContext mixin) throws InvalidMixinException {
        try {
            return new EnumInfo(mixin, mixin.getTarget(), "mixin class");
        } catch (AssumptionViolatedException ex) {
            throw new InvalidMixinException(mixin, ex);
        }
    }

    /**
     * Returns the fields whose type is the type of the enum, excluding stub constants
     */
    public List<FieldNode> getSelfTypedFields() {
        return this.selfTypedFields;
    }

    public List<FieldNode> getConstants() {
        return this.constants;
    }

    public Set<String> getConstantNames() {
        return this.constantNames;
    }

    public FieldInsnNode getValuesAssignment() {
        return this.valuesAssignment;
    }

    public MethodNode getClinit() {
        return this.clinit;
    }

    public boolean isEnumConstantAssignment(AbstractInsnNode insn) {
        if (!(insn instanceof FieldInsnNode)) {
            return false;
        }
        FieldInsnNode fieldInsn = (FieldInsnNode) insn;
        return constantNames.contains(fieldInsn.name) && isAssignmentToOurStaticField(insn, fieldInsn.name, false);
    }

    @Override
    public int compareTo(EnumInfo other) {
        return this.tieBreakString.compareTo(other.tieBreakString);
    }

    private MethodNode findClinit() throws AssumptionViolatedException {
        MethodNode clinit = Bytecode.findMethod(enumClass.getClassNode(), Constants.CLINIT, "()V");
        if (clinit == null) {
            throw assumptionViolated("Failed to find <clinit> in %s", this.description);
        }
        return clinit;
    }

    private FieldNode findValuesField() throws AssumptionViolatedException {
        List<FieldNode> candidates = new ArrayList<>();
        for (FieldNode field : this.enumClass.getClassNode().fields) {
            if (Bytecode.isEnumValuesArray(field, this.targetClass.getClassNode())) {
                candidates.add(field);
            }
        }
        if (candidates.size() != 1) {
            throw assumptionViolated(
                    "Failed to determine enum values array in %s, candidates: %s",
                    this.description, candidates.stream().map(it -> it.name).collect(Collectors.toList())
            );
        }
        return candidates.get(0);
    }

    private FieldInsnNode findValuesAssignment() throws AssumptionViolatedException {
        FieldInsnNode candidate = null;
        for (AbstractInsnNode insn : this.clinit.instructions) {
            if (isAssignmentToOurStaticField(insn, this.valuesField.name, true)) {
                if (candidate == null) {
                    candidate = (FieldInsnNode) insn;
                } else {
                    throw assumptionViolated(
                            "Duplicate enum values assignment in %s (%s)",
                            this.description, this.valuesField.name
                    );
                }
            }
        }
        return candidate;
    }

    private List<FieldNode> findSelfTypedFields() {
        String selfDesc = 'L' + this.targetClass.getClassRef() + ';';
        List<FieldNode> result = new ArrayList<>();
        for (FieldNode field : this.enumClass.getClassNode().fields) {
            if (field.desc.equals(selfDesc) && !isStubEnumConstant(field)) {
                result.add(field);
            }
        }
        return result;
    }

    private List<FieldNode> findEnumConstants() {
        List<FieldNode> result = new ArrayList<>();
        for (FieldNode field : this.selfTypedFields) {
            if (Bytecode.isEnumConstant(field, this.targetClass.getClassNode())) {
                result.add(field);
            }
        }
        return result;
    }

    private boolean isAssignmentToOurStaticField(AbstractInsnNode insn, String fieldName, boolean isArray) {
        if (insn.getOpcode() != Opcodes.PUTSTATIC) {
            return false;
        }
        FieldInsnNode fieldInsn = (FieldInsnNode) insn;
        // We see instructions sometimes before remapping and sometimes after, try both
        for (ClassContext context : new ClassContext[]{this.enumClass, this.targetClass}) {
            if (fieldInsn.owner.equals(context.getClassRef())
                    && fieldInsn.name.equals(fieldName)
                    && fieldInsn.desc.equals((isArray ? "[L" : "L") + context.getClassRef() + ';')) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStubEnumConstant(FieldNode field) {
        return field.attrs != null && field.attrs.stream().anyMatch(attr -> attr.type.equals(STUB_ENUM_CONSTANT_ATTRIBUTE));
    }

    private static AssumptionViolatedException assumptionViolated(String template, Object... args) {
        return new AssumptionViolatedException(String.format(template, args));
    }

    public static final class AssumptionViolatedException extends Exception {
        private AssumptionViolatedException(String message) {
            super(message);
        }
    }
}
