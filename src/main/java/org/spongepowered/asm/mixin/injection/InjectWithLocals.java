package org.spongepowered.asm.mixin.injection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.spongepowered.asm.mixin.MixinEnvironment.Option;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelector;
import org.spongepowered.asm.mixin.injection.throwables.InjectionError;
import org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException;
import org.spongepowered.asm.util.ConstraintParser.Constraint;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InjectWithLocals {
	@Target({/* None */})
	@Retention(RetentionPolicy.RUNTIME)
	@interface Local {
		/**
		 * Gets the local variable ordinal by type. For example, if there are 3
		 * {@link String} arguments in the local variable table, ordinal 0 specifies
		 * the first, 1 specifies the second, etc. Use <tt>ordinal</tt> when the
		 * index within the LVT is known. Takes precedence over {@link #index}.
		 * 
		 * @return variable ordinal
		 */
		public int ordinal() default -1;

		/**
		 * Gets the absolute index of the local variable within the local variable
		 * table to capture. The local variable at the specified index must be of
		 * the same type as the capture. Takes precedence over {@link #name}.
		 * 
		 * @return argument index to modify or -1 for automatic
		 */
		public int index() default -1;

		/**
		 * Gets the name of the variable to capture. Only used if the variable
		 * cannot be located via {@link #ordinal} or {@link #index}.
		 * 
		 * @return possible names to capture, only useful when the LVT in the target
		 *	  method is known to be complete.
		 */
		public String[] name() default {};
	}
	@Target(ElementType.PARAMETER)
	@Retention(RetentionPolicy.RUNTIME)
	@interface Modify {
	}

	/**
	 * The identifier for this injector, can be retrieved via the
	 * {@link CallbackInfo#getId} accessor. If not specified, the ID defaults to
	 * the target method name.
	 * 
	 * @return the injector id to use
	 */
	public String id() default "";

	/**
	 * String representation of one or more
	 * {@link ITargetSelector target selectors} which identify the target
	 * methods.
	 * 
	 * @return target method(s) for this injector
	 */
	public String[] method();

	/**
	 * A {@link Slice} annotation which describes the method bisection used in
	 * the {@link #at} query for this injector.
	 * 
	 * @return slice
	 */
	public Slice slice() default @Slice;

	/**
	 * An {@link At} annotation which describes the {@link InjectionPoint} in
	 * the target method.
	 * 
	 * @return {@link At} which identifies the location to inject inside the
	 *	  target method.
	 */
	public At at();

	/**
	 * Setting an injected callback to <em>cancellable</em> allows the injected
	 * callback to inject optional RETURN opcodes into the target method, the
	 * return behaviour can then be controlled from within the callback by
	 * interacting with the supplied {@link CallbackInfo} object.
	 * 
	 * @return true if this injector should inject appropriate RETURN opcodes
	 *	  which allow it to be cancelled
	 */
	public boolean cancellable() default false;

	/**
	 * Specifies the local variable capture behaviour for this injector.
	 * 
	 * <p>When capturing local variables in scope, the variables are appended to
	 * the callback invocation after the {@link CallbackInfo} argument.</p>
	 * 
	 * <p>Capturing local variables from the target scope requires careful
	 * planning because unlike other aspects of an injection (such as the target
	 * method name and signature), the local variable table is <b>not</b> safe
	 * from modification by other transformers which may be in use in the
	 * production environment. Even other injectors which target the same target
	 * method have the ability to modify the local variable table and thus it is
	 * in no way safe to assume that local variables in scope at development
	 * time will be so in production.</p>
	 * 
	 * <p>To provide some level of flexibility, especially where changes can be
	 * anticipated (for example a well-known mod makes changes which result in a
	 * particular structure for the local variable table) it is possible to
	 * provide <em>overloads</em> for the handler method which will become
	 * surrogate targets for the orphaned injector by annotating them with an
	 * {@link Surrogate} annotation.</p>
	 * 
	 * <p>You can improve the robustness of your local capture injection by only
	 * specifying locals up to the last variable you wish to use. For example if
	 * the target LVT contains <tt>&lt;int, int, int, float, String&gt;</tt> and
	 * you only need the <tt>float</tt> value, you can choose to omit the unused
	 * <tt>String</tt> and changes to the LVT beyond that point will not affect
	 * your injection.</p>
	 * 
	 * <p>It is also important to nominate the failure behaviour to follow when
	 * local capture fails and so all {@link LocalCapture} behaviours which
	 * specify a capture action imply a particular behaviour for handling
	 * failure. See the javadoc on the {@link LocalCapture} members for more
	 * details.</p>
	 * 
	 * <p>Determining what local variables are available to you and in what
	 * order can be somewhat tricky, and so a simple mechanism for enumerating
	 * available locals is provided. By setting <code>locals</code> to
	 * {@link LocalCapture#PRINT}, the injector writes the local capture state
	 * to STDERR instead of injecting the callback. Using the output thus
	 * obtained it is then a straightforward matter of altering the callback
	 * method signature to match the signature proposed by the Callback
	 * Injector.</p> 
	 * 
	 * @return the desired local capture behaviour for this injector
	 */
	public LocalCapture behaviour();

	public Local[] locals() default {};

	/**
	 * By default, the annotation processor will attempt to locate an
	 * obfuscation mapping for all {@link Inject} methods since it is
	 * anticipated that in general the target of a {@link Inject} annotation
	 * will be an obfuscated method in the target class. However since it is
	 * possible to also apply mixins to non-obfuscated targets (or non-
	 * obfuscated methods in obfuscated targets, such as methods added by Forge)
	 * it may be necessary to suppress the compiler error which would otherwise
	 * be generated. Setting this value to <em>false</em> will cause the
	 * annotation processor to skip this annotation when attempting to build the
	 * obfuscation table for the mixin.
	 * 
	 * @return True to instruct the annotation processor to search for
	 *	  obfuscation mappings for this annotation 
	 */
	public boolean remap() default true;

	/**
	 * In general, injectors are intended to "fail soft" in that a failure to
	 * locate the injection point in the target method is not considered an
	 * error condition. Another transformer may have changed the method
	 * structure or any number of reasons may cause an injection to fail. This
	 * also makes it possible to define several injections to achieve the same
	 * task given <em>expected</em> mutation of the target class and the
	 * injectors which fail are simply ignored.
	 * 
	 * <p>However, this behaviour is not always desirable. For example, if your
	 * application depends on a particular injection succeeding you may wish to
	 * detect the injection failure as an error condition. This argument is thus
	 * provided to allow you to stipulate a <b>minimum</b> number of successful
	 * injections for this callback handler. If the number of injections
	 * specified is not achieved then an {@link InjectionError} is thrown at
	 * application time. Use this option with care.</p>
	 * 
	 * @return Minimum required number of injected callbacks, default specified
	 *	  by the containing config
	 */
	public int require() default -1;

	/**
	 * Like {@link #require()} but only enabled if the
	 * {@link Option#DEBUG_INJECTORS mixin.debug.countInjections} option is set
	 * to <tt>true</tt> and defaults to 1. Use this option during debugging to
	 * perform simple checking of your injectors. Causes the injector to throw
	 * a {@link InvalidInjectionException} if the expected number of injections
	 * is not realised.
	 * 
	 * @return Minimum number of <em>expected</em> callbacks, default 1
	 */
	public int expect() default 1;

	/**
	 * Injection points are in general expected to match every candidate
	 * instruction in the target method or slice, except in cases where options
	 * such as {@link At#ordinal} are specified which naturally limit the number
	 * of results.
	 * 
	 * <p>This option allows for sanity-checking to be performed on the results
	 * of an injection point by specifying a maximum allowed number of matches,
	 * similar to that afforded by {@link Group#max}. For example if your
	 * injection is expected to match 4 invocations of a target method, but
	 * instead matches 5, this can become a detectable tamper condition by
	 * setting this value to <tt>4</tt>.
	 * 
	 * <p>Setting any value 1 or greater is allowed. Values less than 1 or less
	 * than {@link #require} are ignored. {@link #require} supercedes this
	 * argument such that if <tt>allow</tt> is less than <tt>require</tt> the
	 * value of <tt>require</tt> is always used.</p>
	 * 
	 * <p>Note that this option is not a <i>limit</i> on the query behaviour of
	 * this injection point. It is only a sanity check used to ensure that the
	 * number of matches is not too high 
	 * 
	 * @return Maximum allowed number of injections for this 
	 */
	public int allow() default -1;

	/**
	 * Returns constraints which must be validated for this injector to
	 * succeed. See {@link Constraint} for details of constraint formats.
	 * 
	 * @return Constraints for this annotation
	 */
	public String constraints() default "";
}