package org.spongepowered.asm.mixin.injection.callback;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;

import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.MixinEnvironment.Option;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.SyntheticClassInfo;
import org.spongepowered.asm.mixin.transformer.ext.IClassGenerator;
import org.spongepowered.asm.service.ISyntheticClassInfo;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Constants;
import org.spongepowered.asm.util.IConsumer;

public class CallbackClassGenerator implements IClassGenerator {
	private static class Key {
		private final Type returnType;
		private boolean useReturn;
		private Type[] locals;

		Key(Type returnType, boolean useReturn, Type[] locals) {
			this.returnType = returnType;
			this.useReturn = useReturn && !Type.VOID_TYPE.equals(returnType);
			this.locals = locals;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) return true;
			if (!(obj instanceof Key)) return false;
			Key that = (Key) obj;

			return returnType.equals(that.returnType) && useReturn == that.useReturn && Arrays.equals(locals, that.locals);
		}

		@Override
		public int hashCode() {
			int out = 1;

			out = 31 * out + returnType.hashCode();
			out = 31 * out + Boolean.hashCode(useReturn);
			out = 31 * out + Arrays.hashCode(locals);

			return out;
		}
	}
	private static class CallbackClassInfo extends SyntheticClassInfo {
		final Type returnType;
		final boolean useReturn;
		final Type[] locals;
		int loaded = 0;

		CallbackClassInfo(IMixinInfo mixin, String name, Key key) {
			super(mixin, name);

			returnType = key.returnType;
			useReturn = key.useReturn;
			locals = key.locals;
		}

		String getSuperType() {
			return CallbackInfo.getCallInfoClassName(returnType);
		}

		String getSuperConstructor() {
			return useReturn ? CallbackInfo.getConstructorDescriptor(returnType) : CallbackInfo.getConstructorDescriptor();
		}

		@Override
		public boolean isLoaded() {
			return this.loaded > 0;
		}
	}
	private static final String CLASS_NAME_BASE = Constants.SYNTHETIC_PACKAGE + ".callback.CallbackInfoWithLocals$";
	private static final String LOCAL_FIELD = "local$";
	static final String SET_LOCALS = "setLocals";
	static final String GET_LOCAL = "getLocal$";
	private static final ILogger logger = MixinService.getService().getLogger("mixin");
	private final IConsumer<ISyntheticClassInfo> registry;
    private final Map<Key, CallbackClassInfo> pool = new HashMap<Key, CallbackClassInfo>();
    private final Map<String, CallbackClassInfo> nameToClass = new HashMap<String, CallbackClassInfo>();
    private int nextIndex = 1;

	public CallbackClassGenerator(IConsumer<ISyntheticClassInfo> registry) {
		this.registry = registry;
	}

	@Override
	public String getName() {
		return "local-callback";
	}

	public ISyntheticClassInfo getArgsClass(IMixinInfo mixin, Type returnType, boolean useReturn, Type... locals) {
		Key key = new Key(returnType, useReturn, locals);

		CallbackClassInfo info = pool.get(key);
		if (info == null) {
			String name = CLASS_NAME_BASE + nextIndex++;
			logger.debug("CallbackClassGenerator generating {} for {} CallbackInfo{} with locals {}", name, useReturn ? "" : "mid-method",
					Type.VOID_TYPE == returnType ? "" : "Returnable (returning " + returnType.getClassName() + ')', Arrays.toString(locals));
			info = new CallbackClassInfo(mixin, name, key);

			pool.put(key, info);
			nameToClass.put(name, info);
			registry.accept(info);
		}

		return info;
	}

	@Override
	public boolean generate(String name, ClassNode classNode) {
		CallbackClassInfo info = this.nameToClass.get(name);
		if (info == null) {
			return false;
		}

		if (info.loaded > 0) {
			logger.debug("CallbackClassGenerator is re-generating {}, already did this {} times!", name, info.loaded);
		}

		ClassVisitor visitor = classNode;
		if (MixinEnvironment.getCurrentEnvironment().getOption(Option.DEBUG_VERIFY)) {
			visitor = new CheckClassAdapter(classNode);
		}

		visitor.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, info.getName(), null, info.getSuperType(), null);
		visitor.visitSource(name.substring(name.lastIndexOf('.') + 1) + ".java", null);

		generateFields(info, visitor);
		generateConstructor(info, visitor);
		generateSetter(info, visitor);
		generateGetters(info, visitor);

		visitor.visitEnd();
		info.loaded++;

		return true;
	}

	private void generateFields(CallbackClassInfo info, ClassVisitor writer) {
		for (int i = 0; i < info.locals.length; i++) {
			writer.visitField(Opcodes.ACC_PRIVATE, LOCAL_FIELD + i, info.locals[i].getDescriptor(), null, null);
		}
	}

	private void generateConstructor(CallbackClassInfo info, ClassVisitor writer) {
		int maxStack = info.useReturn ? 3 + info.returnType.getSize() : 3;

		MethodVisitor ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, Constants.CTOR, info.getSuperConstructor(), null, null);
		ctor.visitCode();
		ctor.visitVarInsn(Opcodes.ALOAD, 0);
		ctor.visitVarInsn(Opcodes.ALOAD, 1);
		ctor.visitVarInsn(Opcodes.ILOAD, 2);
		if (info.useReturn) ctor.visitVarInsn(info.returnType.getOpcode(Opcodes.ILOAD), 3);
		ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, info.getSuperType(), Constants.CTOR, info.getSuperConstructor(), false);
		ctor.visitInsn(Opcodes.RETURN);
		ctor.visitMaxs(maxStack, maxStack);
		ctor.visitEnd();
	}

	private void generateSetter(CallbackClassInfo info, ClassVisitor writer) {
		MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, SET_LOCALS, Type.getMethodDescriptor(Type.VOID_TYPE, info.locals), null, null);
		method.visitCode();
		int index = 1;
		for (int i = 0; i < info.locals.length; i++) {
			Type local = info.locals[i];
			method.visitVarInsn(Opcodes.ALOAD, 0);
			method.visitVarInsn(local.getOpcode(Opcodes.ILOAD), index);
			method.visitFieldInsn(Opcodes.PUTFIELD, info.getName(), LOCAL_FIELD + i, local.getDescriptor());
			index += local.getSize();
		}
		method.visitInsn(Opcodes.RETURN);
		method.visitMaxs(info.locals.length + 1 < index ? 3 : 2, index);
		method.visitEnd();
	}

	private void generateGetters(CallbackClassInfo info, ClassVisitor writer) {
		for (int i = 0; i < info.locals.length; i++) {
			Type local = info.locals[i];

			MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, GET_LOCAL + i, Type.getMethodDescriptor(local), null, null);
			method.visitCode();
			method.visitVarInsn(Opcodes.ALOAD, 0);
			method.visitFieldInsn(Opcodes.GETFIELD, info.getName(), LOCAL_FIELD + i, local.getDescriptor());
			method.visitInsn(local.getOpcode(Opcodes.IRETURN));
			method.visitMaxs(local.getSize(), 1);
			method.visitEnd();
		}
	}
}