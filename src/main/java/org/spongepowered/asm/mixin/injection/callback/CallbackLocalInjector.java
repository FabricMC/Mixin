package org.spongepowered.asm.mixin.injection.callback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Joiner;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.InjectWithLocals.Modify;
import org.spongepowered.asm.mixin.injection.callback.CallbackLocalInjector.CallbackWithLocals.CapturedLocal;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes.InjectionNode;
import org.spongepowered.asm.mixin.injection.throwables.InjectionError;
import org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException;
import org.spongepowered.asm.util.Annotations;
import org.spongepowered.asm.util.Bytecode;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.asm.util.SignaturePrinter;

public class CallbackLocalInjector extends CallbackInjector {
	public static class Local {
		public final int ordinal, index;
		public final Set<String> names;

		public Local(int ordinal, int index, Set<String> names) {
			this.ordinal = ordinal;
			this.index = index;
			this.names = names;
		}

		public boolean isImplicit() {
			return index < 0 && ordinal < 0 && names.isEmpty();
		}

		@Override
		public String toString() {
			return String.format("Local[ordinal=%d, index=%d, names=%s]", ordinal, index, names);
		}
	}
	protected class CallbackWithLocals extends CallbackInjector.Callback implements PrettyPrinter.IPrettyPrintable {
		public class CapturedLocal {
			public final int index;
			public final Type type;
			public final boolean wasImplicit, matchedType, modifying;

			public CapturedLocal(int argOffset, int index, Type type) {
				this(argOffset, index, type, false);
			}

			public CapturedLocal(int argOffset, int index, Type type, boolean wasImplicit) {
				this.index = index;
				this.type = type;
				this.wasImplicit = wasImplicit;
				matchedType = index < -1 || index >= 0 && type.equals(localTypes[index]);
				modifying = willModifyLocals() && Annotations.getVisibleParameter(methodNode, Modify.class, argOffset) != null;
			}

			public boolean isSuccessful() {
				return index >= 0 && matchedType;
			}

			@Override
			public String toString() {
				return String.format("Local[index=%d, type=%s, matching=%b]", index, type, matchedType);
			}	
		}
		final int[] ordinals;
		final CapturedLocal[] capturedLocals;
		private boolean modifiesLocals;
 
		CallbackWithLocals(MethodNode handler, Target target, InjectionNode node, LocalVariableNode[] locals, Local[] localRequests) {
			super(handler, target, node, locals, true);

			this.ordinals = new int[locals.length];
			Arrays.fill(ordinals, -1);
			Map<Type, int[]> ordinalMap = new HashMap<Type, int[]>();
			Map<Type, Integer> typeMap = new HashMap<Type, Integer>();
			Map<String, Integer> nameMap = new HashMap<String, Integer>();
			for (int i = frameSize; i < locals.length; i++) {
				if (locals[i] != null) {
					Type type = localTypes[i];
					int[] ordinals = ordinalMap.get(type);
					int ordinal; //It's like an int list, but more manual
					if (ordinals == null) {
						ordinal = 0;
						ordinals = new int[1];
					} else {
						ordinals = Arrays.copyOf(ordinals, (ordinal = ordinals.length) + 1);
					}
					ordinals[this.ordinals[i] = ordinal] = i;
					ordinalMap.put(type, ordinals);
					Integer typeIndex = typeMap.get(type);
					typeMap.put(type, typeIndex != null ? (typeIndex < 0 ? typeIndex - 1 : -2) : i);
					nameMap.put(locals[i].name, i);
				}
			}

			if (extraArgs < localRequests.length) {//Avoid any accidents immediately below
				if (localCapture.isPrintLocals()) {
					capturedLocals = new CapturedLocal[0];
					return; //Allow the invalid state if we're only printing anyway
				}
				throw new InvalidInjectionException(info, String.format("Too many locals specified in %s! Expected (up to) %d but had %d", info, extraArgs, localRequests.length));
			}
			capturedLocals = new CapturedLocal[extraArgs];
			Type[] handlerArgs = Type.getArgumentTypes(handler.desc);
			/** The point at which local arguments start in the handler */
			int argOffset = target.arguments.length + 1;
			on: for (int i = 0; i < localRequests.length; i++, argOffset++) {
				Local request = localRequests[i];
				Type type = handlerArgs[argOffset];

				if (request.ordinal >= 0) {
					int[] ordinals = ordinalMap.get(type);
					if (ordinals != null && ordinals.length > request.ordinal) {
						capturedLocals[i] = new CapturedLocal(argOffset, ordinals[request.ordinal], type);
						continue;
					}
				}
				if (request.index >= 0 && request.index < locals.length - frameSize) {
					capturedLocals[i] = new CapturedLocal(argOffset, frameSize + request.index, type);
					continue;
				}
				if (!request.names.isEmpty()) {
					for (String name : request.names) {
						Integer index = nameMap.get(name);
						if (index != null) {
							capturedLocals[i] = new CapturedLocal(argOffset, index, type);
							continue on;							
						}
					}
				}
				capturedLocals[i] = request.isImplicit() ? forType(typeMap, type, argOffset) : new CapturedLocal(argOffset, -1, type);
			}
			for (int i = localRequests.length; i < extraArgs; i++, argOffset++) {
				capturedLocals[i] = forType(typeMap, handlerArgs[argOffset], argOffset);
			}
			logger.info(Arrays.toString(capturedLocals));
		}

		private CapturedLocal forType(Map<Type, Integer> typeMap, Type type, int argOffset) {
			Integer typeIndex = typeMap.get(type);
			if (typeIndex == null) {//Type not present at all
				return new CapturedLocal(argOffset, -1, type, true);
			} else {
				return new CapturedLocal(argOffset, typeIndex, type, true);
			}
		}

		@Override
		protected boolean usesCallbackInfo(MethodNode handler, int callbackInfoSlot) {
			for (int i = target.arguments.length + 1, count = 0; count < extraArgs; i++, count++) {
				if (Annotations.getVisibleParameter(handler, Modify.class, i) != null) {
					logger.debug("{} does use it's CallbackInfo{} (for local {})", info, Type.VOID_TYPE == target.returnType ? "" : "Returnable", i);
					return modifiesLocals = true;
				}
			}

			return super.usesCallbackInfo(handler, callbackInfoSlot);
		}

		@Override
		protected String getCallbackInfoClass() {
			if (!modifiesLocals) return super.getCallbackInfoClass();
			List<Type> modifyingLocals = new ArrayList<Type>(capturedLocals.length);

			for (CapturedLocal local : capturedLocals) {
				if (local.modifying) {
					modifyingLocals.add(local.type);
				}
			}

			return classGenerator.getArgsClass(info.getMixin().getMixin(), target.returnType, isAtReturn, modifyingLocals.toArray(new Type[0])).getName();
		}

		boolean willModifyLocals() {
			return modifiesLocals;
		}

		@Override
		boolean checkDescriptor(String desc) {
			Type[] args = Type.getArgumentTypes(desc);
			int i = 0;

			for (Type param : target.arguments) {
				if (i >= args.length || !param.equals(args[i++])) {//Missing the target method's parameters
					throw new InvalidInjectionException(info, String.format("Invalid descriptor on %s! Target parameter%s missing/incorrect, expected %s", info,
							target.arguments.length > 1 ? "s are" : " is", target.method.desc.substring(1, target.method.desc.indexOf(')'))));					
				}
			}

			Type callback = Type.getObjectType(target.getCallbackInfoClass());
			if (i >= args.length || !callback.equals(args[i])) {//Callback is wrong, perhaps a returnable one has/n't been used when it should/n't
				callback = Type.getObjectType(CallbackInfo.getCallInfoClassName(Type.VOID_TYPE.equals(target.returnType) ? Type.INT_TYPE : Type.VOID_TYPE));

				if (i < args.length && callback.equals(args[i])) {//Wrong callback it is
					String correct = callback.getInternalName();
					throw new InvalidInjectionException(info, String.format("Invalid descriptor on %s! %s is required!", info, correct.substring(correct.lastIndexOf('/') + 1)));
				} else {//Still not right...
					String correct = target.getCallbackInfoClass();
					throw new InvalidInjectionException(info, String.format("Invalid descriptor on %s! Expected %s after target parameter%s but found %s", info,
							target.arguments.length > 1 ? "s" : "", correct.substring(correct.lastIndexOf('/') + 1), args.length > i ? args[i] : "<nothing>"));
				}
			} else i++;

			for (CapturedLocal local : capturedLocals) {
				if (!local.isSuccessful() || i >= args.length || !local.type.equals(args[i++])) {
					return false;
				}
			}

			return true;
		}

		@Override
		public void print(PrettyPrinter printer) {
			printer.add("  %5s  %7s  %30s  %s", "INDEX", "ORDINAL", "TYPE", "NAME");
			for (int i = isStatic() ? 0 : 1; i < frameSize; i++) {
				if (locals[i] != null) {
					printer.add("  PARAM   [ x ]   %30s  %-50s", SignaturePrinter.getTypeName(localTypes[i], false), CallbackInjector.meltSnowman(i, locals[i].name));
				} else {
					boolean isTop = i > 0 && localTypes[i - 1] != null && localTypes[i - 1].getSize() > 1;
					printer.add("  PARAM           %30s", isTop ? "<top>" : "-");
				}
			}
			boolean[] captures = new boolean[locals.length];
			for (CapturedLocal local : capturedLocals) {
				if (local.index >= 0) captures[local.index] = true;
			}
			for (int i = frameSize, index = 0; i < locals.length; i++, index++) {
				char marker = i == frameSize ? '>' : ' ';
				if (locals[i] != null) {
					printer.add("%c [%3d]   [%3d]   %30s  %-50s <%s>", marker, index, ordinals[i], SignaturePrinter.getTypeName(localTypes[i], false),
							CallbackInjector.meltSnowman(i, locals[i].name), captures[i] ? "captured" : "skipped");
				} else {
					boolean isTop = i > 0 && localTypes[i - 1] != null && localTypes[i - 1].getSize() > 1;
					printer.add("%c [%3d]           %30s", marker, index, isTop ? "<top>" : "-");
				}
			}
		}
	}
	private final Local[] localRequests;
	final CallbackClassGenerator classGenerator;

	public CallbackLocalInjector(InjectionInfo info, boolean cancellable, LocalCapture behaviour, Local[] locals, String identifier) {
		super(info, "@InjectWithLocals", cancellable, behaviour, identifier);

		localRequests = locals;
		classGenerator = info.getMixin().getExtensions().<CallbackClassGenerator>getGenerator(CallbackClassGenerator.class);
	}

	@Override
	protected void sanityCheck(Target target, List<InjectionPoint> injectionPoints) {
		super.sanityCheck(target, injectionPoints);

		if (localCapture == null || localCapture == LocalCapture.NO_CAPTURE) {
			throw new InvalidInjectionException(info, String.format("Invalid value of local capture behaviour (%s) in %s", localCapture, this));
		}
	}

	@Override
	protected void inject(Target target, InjectionNode node) {
		LocalVariableNode[] locals = node.<LocalVariableNode[]>getDecoration(CallbackInjector.LOCALS_KEY + ':' + FabricUtil.getCompatibility(info));
		CallbackWithLocals callback = new CallbackWithLocals(methodNode, target, node, locals, localRequests);

		if (localCapture.isPrintLocals()) {
			printLocals(callback);
			info.addCallbackInvocation(methodNode);
			return;
		}

		MethodNode callbackMethod;
		if (!callback.checkDescriptor(methodNode.desc)) {
			if (info.getTargetCount() > 1) {
				return; // Look for a match in other targets before failing
			}

			String message = generateBadLocalsMessage(callback);
			switch (localCapture) {
				case CAPTURE_FAILEXCEPTION:
					logger.error("Injection error: {}", message);
					callbackMethod = generateErrorMethod(callback, "org/spongepowered/asm/mixin/injection/throwables/InjectionError", message);
					break;
				case CAPTURE_FAILSOFT:
					logger.warn("Injection warning: {}", message);
					return;
				default:
					logger.error("Critical injection failure: {}", message);
					throw new InjectionError(message);
			}
		} else {
			callbackMethod = methodNode;
		}

		prepareCallbackIfNeeded(callback, callback.willModifyLocals());
		invokeCallback(callback, callbackMethod);
		if (callback.usesCallbackInfo) injectCancellationCode(callback);

		callback.inject();
		info.notifyInjected(callback.target);
	}

	private void printLocals(CallbackWithLocals callback) {
		PrettyPrinter printer = new PrettyPrinter();

		printer.kv("Target Class", classNode.name.replace('/', '.'));
		printer.kv("Target Method", new SignaturePrinter(callback.target.method, callback.argNames));
		printer.kv("Target Max LOCALS", callback.target.getMaxLocals());
		printer.kv("Initial Frame Size", callback.frameSize);
		printer.kv("Callback Name", info.getMethodName());
		printer.kv("Instruction", "%s %s", callback.node.getClass().getSimpleName(), Bytecode.getOpcodeName(callback.node.getCurrentTarget().getOpcode()));
		printer.hr();
		printer.add(callback).print(System.err);
	}

	private String generateBadLocalsMessage(CallbackWithLocals callback) {
		Type[] args = Type.getArgumentTypes(methodNode.desc);
		List<String> errors = new ArrayList<String>();
		errors.add("Failed to capture all locals:");

		for (int i = 0, index = callback.target.arguments.length + 1; i < callback.capturedLocals.length; i++, index++) {
			CapturedLocal local = callback.capturedLocals[i];

			String error;
			if (!local.isSuccessful()) {
				if (local.index == -1) {
					if (local.wasImplicit) {
						error = "No local with type not found";
					} else {
						error = "No local found matching criteria";
					}
				} else if (!local.matchedType) {
					error = "Wrong type for slot, expected " + callback.localTypes[local.index].getClassName();
				} else {
					error = "Expected one local with type but found " + -local.index;
				}
			} else if (index >= args.length) {//Shouldn't be allowed to happen but we'll cover the case anyway
				error = "Missing parameter for local";
			} else if (!local.type.equals(args[index])) {
				error = "Wrong parameter type for local, expected " + local.type.getClassName();
			} else {
				continue;
			}

			errors.add(String.format("[%2d] %s - %s", i, SignaturePrinter.getTypeName(local.type, false), error));
		}

		return Joiner.on("\n\t").join(errors);
	}

	private void invokeCallback(CallbackWithLocals callback, MethodNode callbackMethod) {
		// Push "this" onto the stack if the callback is not static
		if (!isStatic) {
			callback.add(new VarInsnNode(Opcodes.ALOAD, 0), false, true);
		}

		// Push the target method's parameters onto the stack
		if (callback.captureArgs()) {
			Bytecode.loadArgs(callback.target.arguments, callback, isStatic ? 0 : 1, -1);
		}

		// Push the callback info onto the stack
		loadOrCreateCallbackInfo(callback, callback.willModifyLocals());

		// Push the locals onto the stack
		for (CapturedLocal local : callback.capturedLocals) {
			callback.add(new VarInsnNode(local.type.getOpcode(Opcodes.ILOAD), local.index));
		}

		// Call the callback!
		invokeHandler(callback, callbackMethod);

		// Capture changes to locals in the handler
		if (callback.willModifyLocals() && Annotations.getInvisible(callbackMethod, ModificationsCaught.class) == null) {
			String callbackType = callback.getCallbackInfoClass();

			for (AbstractInsnNode insn : callbackMethod.instructions) {
				if (insn.getType() == AbstractInsnNode.INSN && insn.getOpcode() >= Opcodes.IRETURN && insn.getOpcode() <= Opcodes.RETURN) {
					InsnList list = new InsnList();
					list.add(new VarInsnNode(Opcodes.ALOAD, callback.frameSize));
					list.add(new TypeInsnNode(Opcodes.CHECKCAST, callbackType));
					StringBuilder desc = new StringBuilder("(");
					int slot = callback.frameSize + 1;
					for (CapturedLocal local : callback.capturedLocals) {
						if (local.modifying) {
							desc.append(local.type.getDescriptor());
							list.add(new VarInsnNode(local.type.getOpcode(Opcodes.ILOAD), slot));
						}

						slot += local.type.getSize();
					}
					list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, callbackType, CallbackClassGenerator.SET_LOCALS, desc.append(")V").toString()));
					callbackMethod.instructions.insertBefore(insn, list);
				}
			}

			Annotations.setInvisible(callbackMethod, ModificationsCaught.class);
		}
	}

	@Override
	protected void injectCancellationCode(Callback callback) {
		super.injectCancellationCode(callback);

		CallbackWithLocals callbackLocal;
		if (callback instanceof CallbackWithLocals && (callbackLocal = (CallbackWithLocals) callback).willModifyLocals()) {
			String callbackType = callback.getCallbackInfoClass();

			for (int i = 0, used = 0; i < callbackLocal.capturedLocals.length; i++) {
				CapturedLocal local = callbackLocal.capturedLocals[i];
				if (!local.modifying) continue;

				callback.add(new VarInsnNode(Opcodes.ALOAD, callback.marshalVar()));
				callback.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, callbackType, CallbackClassGenerator.GET_LOCAL + used++, Type.getMethodDescriptor(local.type)));
				callback.add(new VarInsnNode(local.type.getOpcode(Opcodes.ISTORE), local.index));
			}
		}
	}
}