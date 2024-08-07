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
package org.spongepowered.asm.mixin.injection.invoke;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint.RestrictTargetLevel;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.ArgsClassGenerator;
import org.spongepowered.asm.mixin.injection.struct.ArgOffsets;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes.InjectionNode;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.mixin.injection.struct.Target.Extension;
import org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException;
import org.spongepowered.asm.util.Bytecode;

/**
 * A bytecode injector which allows a single argument of a chosen method call to
 * be altered. For details see javadoc for {@link ModifyArgs &#64;ModifyArgs}.
 */
public class ModifyArgsInjector extends InvokeInjector {

    private final ArgsClassGenerator argsClassGenerator;

    /**
     * @param info Injection info
     */
    public ModifyArgsInjector(InjectionInfo info) {
        super(info, "@ModifyArgs");
        
        this.argsClassGenerator = info.getMixin().getExtensions().<ArgsClassGenerator>getGenerator(ArgsClassGenerator.class);
    }
    
    /* (non-Javadoc)
     * @see org.spongepowered.asm.mixin.injection.invoke.InvokeInjector
     *      #checkTarget(org.spongepowered.asm.mixin.injection.struct.Target)
     */
    @Override
    protected void checkTarget(Target target) {
        this.checkTargetModifiers(target, false);
    }
    
    @Override
    protected void inject(Target target, InjectionNode node) {
        this.checkTargetForNode(target, node, RestrictTargetLevel.ALLOW_ALL);
        super.inject(target, node);
    }

    /**
     * Do the injection
     */
    @Override
    protected void injectAtInvoke(Target target, InjectionNode node) {
        MethodInsnNode methodNode = (MethodInsnNode)node.getCurrentTarget();
        Type[] args = Type.getArgumentTypes(methodNode.desc);
        ArgOffsets offsets = node.<ArgOffsets>getDecoration(ArgOffsets.KEY, ArgOffsets.DEFAULT);
        Type[] originalArgs = offsets.apply(args);
        int endIndex = offsets.getArgIndex(originalArgs.length);
        
        String targetMethodDesc = Type.getMethodDescriptor(Type.getReturnType(methodNode.desc), originalArgs);
        
        if (originalArgs.length == 0) {
            throw new InvalidInjectionException(this.info, "@ModifyArgs injector " + this + " targets a method invocation "
                    + ((MethodInsnNode)node.getOriginalTarget()).name + targetMethodDesc + " with no arguments!");
        }
        
        String clArgs = this.argsClassGenerator.getArgsClass(targetMethodDesc, this.info.getMixin().getMixin()).getName();
        boolean withArgs = this.verifyTarget(target);

        InsnList insns = new InsnList();
        Extension extraStack = target.extendStack().add(1);
        
        int[] afterWindowArgMap = this.storeArgs(target, args, insns, endIndex);
        this.packArgs(insns, clArgs, targetMethodDesc);
        
        if (withArgs) {
            extraStack.add(target.arguments);
            Bytecode.loadArgs(target.arguments, insns, target.isStatic ? 0 : 1);
        }
        
        this.invokeHandler(insns);
        this.unpackArgs(insns, clArgs, originalArgs);
        this.pushArgs(args, insns, afterWindowArgMap, endIndex, args.length);

        extraStack.apply();
        target.insns.insertBefore(methodNode, insns);
    }

    private boolean verifyTarget(Target target) {
        String shortDesc = String.format("(L%s;)V", ArgsClassGenerator.ARGS_REF);
        if (!this.methodNode.desc.equals(shortDesc)) {
            String targetDesc = Bytecode.changeDescriptorReturnType(target.getDesc(), "V");
            String longDesc = String.format("(L%s;%s", ArgsClassGenerator.ARGS_REF, targetDesc.substring(1));
            
            if (this.methodNode.desc.equals(longDesc)) {
                return true;
            }
            
            throw new InvalidInjectionException(this.info, "@ModifyArgs injector " + this + " has an invalid signature "
                    + this.methodNode.desc + ", expected " + shortDesc + " or " + longDesc);
        }
        return false;
    }

    private void packArgs(InsnList insns, String clArgs, String targetMethodDesc) {
        String factoryDesc = Bytecode.changeDescriptorReturnType(targetMethodDesc, "L" + clArgs + ";");
        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, clArgs, "of", factoryDesc, false));
        insns.add(new InsnNode(Opcodes.DUP));
        
        if (!this.isStatic) {
            insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
            insns.add(new InsnNode(Opcodes.SWAP));
        }
    }

    private void unpackArgs(InsnList insns, String clArgs, Type[] args) {
        for (int i = 0; i < args.length; i++) {
            if (i < args.length - 1) {
                insns.add(new InsnNode(Opcodes.DUP));
            }
            insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, clArgs, ArgsClassGenerator.GETTER_PREFIX + i, "()" + args[i].getDescriptor(), false));
            if (i < args.length - 1) {
                if (args[i].getSize() == 1) {
                    insns.add(new InsnNode(Opcodes.SWAP));
                } else {
                    insns.add(new InsnNode(Opcodes.DUP2_X1));
                    insns.add(new InsnNode(Opcodes.POP2));
                }
            }
        }
    }
}
