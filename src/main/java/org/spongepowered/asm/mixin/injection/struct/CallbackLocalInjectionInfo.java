package org.spongepowered.asm.mixin.injection.struct;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;

import org.spongepowered.asm.mixin.injection.InjectWithLocals;
import org.spongepowered.asm.mixin.injection.callback.CallbackLocalInjector;
import org.spongepowered.asm.mixin.injection.callback.CallbackLocalInjector.Local;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.asm.mixin.injection.code.Injector;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo.AnnotationType;
import org.spongepowered.asm.mixin.transformer.MixinTargetContext;
import org.spongepowered.asm.util.Annotations;

/**
 * Information about a callback to inject, usually specified by {@link InjectWithLocals}
 */
@AnnotationType(InjectWithLocals.class)
public class CallbackLocalInjectionInfo extends CallbackInjectionInfo {
	protected CallbackLocalInjectionInfo(MixinTargetContext mixin, MethodNode method, AnnotationNode annotation) {
		super(mixin, method, annotation);
	}

	@Override
	protected Injector parseInjector(AnnotationNode injectAnnotation) {
		boolean cancellable = Annotations.<Boolean>getValue(injectAnnotation, "cancellable", Boolean.FALSE);
		LocalCapture behaviour = Annotations.<LocalCapture>getValue(injectAnnotation, "behaviour", LocalCapture.class, null);
		List<AnnotationNode> localNodes = Annotations.<AnnotationNode>getValue(injectAnnotation, "locals", true);
		Local[] locals = new Local[localNodes.size()];
		int i = 0;
		for (AnnotationNode local : localNodes) {
			int ordinal = Annotations.<Integer>getValue(local, "ordinal", -1);
			int index = Annotations.<Integer>getValue(local, "index", -1);
			List<String> names = Annotations.<List<String>>getValue(local, "name", Collections.<String>emptyList());
			locals[i++] = new Local(ordinal, index, !names.isEmpty() ? new HashSet<String>(names) : Collections.<String>emptySet());
		}
		String identifier = Annotations.<String>getValue(injectAnnotation, "id", "");

		return new CallbackLocalInjector(this, cancellable, behaviour, locals, identifier);
	}

	@Override
	protected String getDescription() {
		return "Callback method with locals";
	}
}