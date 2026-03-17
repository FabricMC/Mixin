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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.MixinIntrinsics;
import org.spongepowered.asm.mixin.extensibility.IActivityContext;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.mixin.transformer.struct.Clinit;
import org.spongepowered.asm.mixin.transformer.throwables.InvalidMixinException;
import org.spongepowered.asm.util.Bytecode;
import org.spongepowered.asm.util.Constants;

import java.util.*;
import java.util.stream.Collectors;

class MixinApplicatorEnum extends MixinApplicatorStandard {
    private final Map<IMixinInfo, EnumInfo> extensionInfos = new HashMap<>();
    private EnumInfo targetInfo;
    private int ordinalShift;
    private FieldNode insertionPoint;

    MixinApplicatorEnum(TargetClassContext context) {
        super(context);
    }

    @Override
    protected void preApply(IActivityContext.IActivity activity, List<MixinTargetContext> mixins) throws Exception {
        this.gatherEnumExtensions(mixins);
        this.sortEnumExtensions(mixins);

        // Pre-apply in the right order now
        super.preApply(activity, mixins);

        // Target class is now ready
        if (!this.extensionInfos.isEmpty()) {
            this.prepareTargetInfo(mixins);
            this.checkUniqueEnumConstants(mixins);
            this.permitEnumSubclasses(mixins);
            this.replaceValueOf();
        }
    }

    private void gatherEnumExtensions(List<MixinTargetContext> mixins) {
        for (Iterator<MixinTargetContext> iter = mixins.iterator(); iter.hasNext(); ) {
            MixinTargetContext mixin = iter.next();
            if (!mixin.getClassInfo().isEnum()) {
                // Not an enum extension
                continue;
            }
            try {
                this.extensionInfos.put(mixin.getInfo(), EnumInfo.forMixin(mixin));
            } catch (InvalidMixinException ex) {
                if (mixin.isRequired()) {
                    throw ex;
                }
                this.context.addSuppressed(ex);
                iter.remove(); // Do not process this mixin further
            }
        }
    }

    private void sortEnumExtensions(List<MixinTargetContext> mixins) {
        Comparator<MixinTargetContext> byEnumExtension =
                Comparator.comparing(
                        mixin -> this.extensionInfos.get(mixin.getInfo()),
                        Comparator.nullsLast(Comparator.naturalOrder())
                );
        mixins.sort(Comparator.comparing(MixinTargetContext::getPriority).thenComparing(byEnumExtension));
    }

    private void prepareTargetInfo(List<MixinTargetContext> mixins) {
        try {
            this.targetInfo = EnumInfo.forTarget(this.context);
            List<FieldNode> targetConstants = this.targetInfo.getConstants();
            this.ordinalShift = targetConstants.size();
            if (!targetConstants.isEmpty()) {
                this.insertionPoint = targetConstants.get(targetConstants.size() - 1);
            }
        } catch (EnumInfo.AssumptionViolatedException e) {
            // We can't enum extend this target. Blame all mixins who tried to.
            for (Iterator<MixinTargetContext> iter = mixins.iterator(); iter.hasNext(); ) {
                MixinTargetContext mixin = iter.next();

                if (!mixin.getClassInfo().isEnum()) {
                    // Not an enum extension
                    continue;
                }

                InvalidMixinException wrapped = new InvalidMixinException(mixin, e);
                if (mixin.isRequired()) {
                    throw wrapped;
                }
                this.context.addSuppressed(wrapped);
                iter.remove(); // Do not process this mixin further
            }
        }
    }

    private void checkUniqueEnumConstants(List<MixinTargetContext> mixins) {
        Multimap<String, MixinTargetContext> existingSources = ArrayListMultimap.create();

        for (FieldNode field : this.targetInfo.getSelfTypedFields()) {
            existingSources.put(field.name, null);
        }

        for (MixinTargetContext mixin : mixins) {
            for (FieldNode field : mixin.getFields()) {
                if (field.desc.equals('L' + this.targetClass.name + ';')) {
                    existingSources.put(field.name, mixin);
                }
            }
        }

        for (Iterator<MixinTargetContext> iter = mixins.iterator(); iter.hasNext(); ) {
            MixinTargetContext mixin = iter.next();

            EnumInfo extension = this.extensionInfos.get(mixin.getInfo());
            if (extension == null) {
                continue;
            }

            for (FieldNode constant : extension.getConstants()) {
                Collection<MixinTargetContext> sources = existingSources.get(constant.name);
                if (sources.size() >= 2) {
                    // There is a conflict
                    List<String> others =
                            sources.stream()
                                    .filter(it -> it != mixin)
                                    .map(it -> it == null ? "target class" : it.toString())
                                    .collect(Collectors.toList());
                    InvalidMixinException e = new InvalidMixinException(
                            mixin,
                            String.format(
                                    "Added enum constant %s conflicts with field declared in %s",
                                    constant.name, others
                            )
                    );
                    if (mixin.isRequired()) {
                        throw e;
                    }
                    this.context.addSuppressed(e);
                    iter.remove(); // Do not process this mixin further
                    this.extensionInfos.remove(mixin.getInfo());
                    break;
                }
            }
        }
    }

    private void permitEnumSubclasses(List<MixinTargetContext> mixins) {
        if (this.targetClass.permittedSubclasses == null || this.targetClass.permittedSubclasses.isEmpty()) {
            // Not sealed in the first place
            return;
        }
        for (MixinTargetContext mixin : mixins) {
            if (!mixin.getClassInfo().isEnum()) {
                // Not an enum extension
                continue;
            }
            for (Map.Entry<String, String> names : mixin.getInnerClasses().entrySet()) {
                ClassInfo innerClass = ClassInfo.forName(names.getKey());
                if (innerClass.getSuperName().equals(mixin.getClassRef())) {
                    // This is an enum subclass
                    this.targetClass.permittedSubclasses.add(names.getValue());
                }
            }
        }
    }

    /**
     * Replaces the valueOf method with one that delegates to Enum#valueOf, in case the existing
     * implementation is specific to the existing constants.
     */
    private void replaceValueOf() {
        String valueOfDesc = "(Ljava/lang/String;)L" + this.targetClass.name + ';';
        MethodNode existing = Bytecode.findMethod(this.targetClass, "valueOf", valueOfDesc);
        this.targetClass.methods.remove(existing);

        MethodNode valueOf = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "valueOf", valueOfDesc, null, null);
        this.targetClass.methods.add(valueOf);

        valueOf.visitLdcInsn(Type.getObjectType(this.targetClass.name));
        valueOf.visitVarInsn(Opcodes.ALOAD, 0);
        valueOf.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(Enum.class),
                "valueOf",
                "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;",
                false
        );
        valueOf.visitTypeInsn(Opcodes.CHECKCAST, this.targetClass.name);
        valueOf.visitInsn(Opcodes.ARETURN);
        valueOf.visitMaxs(2, 1);
    }

    @Override
    protected void applyNormalMethod(MixinTargetContext mixin, MethodNode mixinMethod) {
        if (mixin.getClassInfo().isEnum() && Constants.CTOR.equals(mixinMethod.name) && Bytecode.hasFlag(mixinMethod, Opcodes.ACC_SYNTHETIC)) {
            // Unusually, we actually want to merge this constructor.
            // It will be from pre-Java 11 and will have in its descriptor one of this Mixin's inner classes, to emulate
            // a nesting system. Because of this it cannot conflict with any other mixins, and the easiest way to keep
            // things working is to merge it.
            mixin.transformMethod(mixinMethod);
            super.mergeMethod(mixin, mixinMethod);
            return;
        }
        super.applyNormalMethod(mixin, mixinMethod);
    }

    @Override
    protected void mergeNormalField(MixinTargetContext mixin, FieldNode field, int index) {
        if (mixin.getClassInfo().isEnum()) {
            if (Bytecode.isEnumConstant(field, targetClass)) {
                // Skip merging for now, we'll do it later in order
                return;
            }
            if (Bytecode.isEnumValuesArray(field, targetClass)) {
                // Skip merging entirely
                return;
            }
        }
        super.mergeNormalField(mixin, field, index);
    }

    @Override
    protected void applyFields(MixinTargetContext mixin) {
        super.applyFields(mixin);

        EnumInfo extension = this.extensionInfos.get(mixin.getInfo());
        if (extension != null) {
            this.applyEnumFields(extension, mixin);
        }
    }

    private void applyEnumFields(EnumInfo extension, MixinTargetContext mixin) {
        List<FieldNode> targetFields = this.targetClass.fields;
        int insertionIndex = this.insertionPoint == null ? 0 : targetFields.lastIndexOf(this.insertionPoint) + 1;

        for (FieldNode constant : extension.getConstants()) {
            super.mergeNormalField(mixin, constant, insertionIndex++);
            this.insertionPoint = constant;
        }
    }

    @Override
    protected Clinit prepareOrCreateClinit() {
        return new EnumClinit();
    }
    
    private class EnumClinit extends Clinit {
        public EnumClinit() {
            this(MixinApplicatorEnum.this.context.getTargetMethod(MixinApplicatorEnum.this.targetInfo.getClinit()));
        }
        
        private EnumClinit(Target clinit) {
            super(clinit.method, Clinit.prepareClinit(clinit.method, clinit));
        }

        @Override
        protected void appendInsns(IMixinInfo mixinInfo, MethodNode mixinClinit, Map<LabelNode, LabelNode> labels) {
            EnumInfo extension = MixinApplicatorEnum.this.extensionInfos.get(mixinInfo);
            if (extension == null) {
                // Not an enum extension
                super.appendInsns(mixinInfo, mixinClinit, labels);
                return;
            }
            
            spliceEnumClinit(mixinInfo, extension, labels);
        }

        private void spliceEnumClinit(IMixinInfo mixinInfo, EnumInfo extension, Map<LabelNode, LabelNode> labels) {
            Set<String> remainingConstants = new HashSet<>(extension.getConstantNames());
            InsnList dest = this.clinit.instructions;
            AbstractInsnNode insertPoint = MixinApplicatorEnum.this.targetInfo.getValuesAssignment();

            InsnList insns = extension.getClinit().instructions;

            // Splice in the enum constant initializers
            Integer currentOrdinal = null;
            for (AbstractInsnNode insn = insns.getFirst(); insn != extension.getValuesAssignment(); insn = insn.getNext()) {
                if (currentOrdinal == null) {
                    if (!remainingConstants.isEmpty()) {
                        Object ordinal = Bytecode.getConstant(insn);
                        if (ordinal instanceof Integer) {
                            // Shift the ordinal up
                            currentOrdinal = (int) ordinal + MixinApplicatorEnum.this.ordinalShift;
                            dest.insertBefore(insertPoint, Bytecode.loadIntConstant(currentOrdinal));
                            continue;
                        }
                    }
                } else if (isCurrentOrdinalCall(insn)) {
                    dest.insertBefore(insertPoint, Bytecode.loadIntConstant(currentOrdinal));
                    continue;
                }

                dest.insertBefore(insertPoint, insn.clone(labels));

                if (extension.isEnumConstantAssignment(insn)) {
                    String constantName = ((FieldInsnNode) insn).name;
                    if (!remainingConstants.remove(constantName)) {
                        throw new InvalidMixinException(mixinInfo, "Duplicate assignment to enum constant " + constantName);
                    }
                    currentOrdinal = null;
                    // Wait for the next one
                }
            }

            if (!remainingConstants.isEmpty()) {
                throw new InvalidMixinException(mixinInfo, "Enum constants not assigned: " + remainingConstants);
            }

            // We have the old array and the new array on the stack, concat them
            insns.insertBefore(insertPoint, concatEnumValues());

            // Merge in the rest of <clinit>
            for (AbstractInsnNode insn = extension.getValuesAssignment().getNext(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() != Opcodes.RETURN) {
                    dest.insertBefore(this.finalReturn, insn.clone(labels));
                }
            }

            // Shift future ordinals up even more
            MixinApplicatorEnum.this.ordinalShift += extension.getConstants().size();
        }

        private InsnList concatEnumValues() {
            InsnList result = new InsnList();
            result.add(
                    new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            Type.getInternalName(MixinHooks.class),
                            "concatEnumValues",
                            "([Ljava/lang/Enum;[Ljava/lang/Enum;)[Ljava/lang/Enum;",
                            false
                    )
            );
            result.add(new TypeInsnNode(Opcodes.CHECKCAST, "[L" + MixinApplicatorEnum.this.targetClass.name + ';'));
            return result;
        }

        private boolean isCurrentOrdinalCall(AbstractInsnNode insn) {
            if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
                return false;
            }
            MethodInsnNode call = (MethodInsnNode) insn;
            return call.owner.equals(Type.getInternalName(MixinIntrinsics.class)) && call.name.equals("currentEnumOrdinal");
        }
    }

}
