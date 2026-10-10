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
package org.spongepowered.asm.mixin.injection.selectors;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.MixinEnvironment.Option;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelector.Configure;
import org.spongepowered.asm.mixin.injection.selectors.throwables.SelectorConstraintException;
import org.spongepowered.asm.mixin.injection.struct.InvalidMemberDescriptorException;
import org.spongepowered.asm.mixin.injection.struct.TargetNotSupportedException;
import org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException;
import org.spongepowered.asm.mixin.refmap.IMixinContext;
import org.spongepowered.asm.mixin.struct.AnnotatedMethodInfo;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Annotations;
import org.spongepowered.asm.util.Bytecode;
import org.spongepowered.asm.util.NameAndDesc;

import com.google.common.collect.Lists;

public class TargetSelectors implements Iterable<TargetSelectors.SelectedMethod> {

    private static final ILogger logger = MixinService.getService().getLogger("mixin");
    
    /**
     * Selected target method
     */
    public static class SelectedMethod {

        /**
         * The selected target method
         */
        private final MethodNode method;

        SelectedMethod(MethodNode method) {
            this.method = method;
        }
        
        @Override
        public String toString() {
            return this.method.name + this.method.desc;
        }

        public MethodNode getMethod() {
            return this.method;
        }

    }
    
    /**
     * The selector context for these selectors, for example the injector which
     * is running the selectors
     */
    private final ISelectorContext context;
    
    /**
     * The target class node within which targets can be resolved
     */
    private final ClassNode targetClassNode;
    
    /**
     * The mixin
     */
    private final IMixinContext mixin;
    
    /**
     * Annotated method, as MethodNode at runtime, or IAnnotatedElement during
     * compile
     */
    private final Object method;

    /**
     * Whether the annotated method is static
     */
    private final boolean isStatic;

    /**
     * An index of methods within the target class
     */
    private final Map<NameAndDesc, MethodNode> methodIndex;

    /**
     * Whether we have already scraped lambda information
     */
    private boolean scrapedLambdas = false;

    /**
     * An index of the lambdas contained within each method, populated lazily
     */
    private final Map<MethodNode, List<ElementNode<?>>> containedLambdas = new IdentityHashMap<>();

    /**
     * All the methods which are lambdas, populated lazily
     */
    private final Set<MethodNode> lambdaMethods = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Root selectors
     */
    private final Set<ITargetSelector> selectors = new LinkedHashSet<ITargetSelector>();
    
    /**
     * Selected targets
     */
    private final List<SelectedMethod> targets = new ArrayList<SelectedMethod>();

    public TargetSelectors(ISelectorContext context, ClassNode classNode) {
        this.context = context;
        this.targetClassNode = classNode; 
        this.mixin = context.getMixin();
        this.method = context.getMethod();       
        this.isStatic = this.method instanceof MethodNode && Bytecode.isStatic((MethodNode)this.method);
        this.methodIndex = classNode.methods.stream()
                .collect(Collectors.toMap(NameAndDesc::new, Function.identity(), (a, b) -> a));
    }

    public void parse(Set<ITargetSelector> selectors) {
        // Validate and attach the parsed selectors
        for (ITargetSelector selector : selectors) {
            try {
                this.validateSelector(selector);
                this.addSelector(selector.attach(this.context));
            } catch (InvalidMemberDescriptorException ex) {
                throw new InvalidInjectionException(this.context, String.format("%s, has invalid target descriptor: %s. %s",
                        this.context.getElementDescription(), ex.getMessage(), this.mixin.getReferenceMapper().getStatus()));
            } catch (TargetNotSupportedException ex) {
                throw new InvalidInjectionException(this.context, String.format("%s specifies a target class '%s', which is not supported",
                        this.context.getElementDescription(), ex.getMessage()));
            } catch (InvalidSelectorException ex) {
                throw new InvalidInjectionException(this.context, String.format("%s is decorated with an invalid selector: %s",
                        this.context.getElementDescription(), ex.getMessage()));
            }
        }
    }

    private void validateSelector(ITargetSelector selector) {
        selector.validate();
        if (FabricUtil.getCompatibility(this.context) >= FabricUtil.COMPATIBILITY_0_17_5) {
            selector.validateNext();
        }
    }

    public TargetSelectors addSelector(ITargetSelector selector) {
        this.selectors.add(selector);
        return this;
    }
    
    public int size() {
        return this.targets.size();
    }
    
    public void clear() {
        this.targets.clear();
    }

    @Override
    public Iterator<SelectedMethod> iterator() {
        return this.targets.iterator();
    }
    
    public void remove(SelectedMethod target) {
        this.targets.remove(target);
    }

    /**
     * Find methods in the target class which match the parsed selectors
     */
    public void find() {
        LinkedHashSet<MethodNode> selected = new LinkedHashSet<>();

        for (ITargetSelector selector : this.selectors) {
            List<MethodNode> roots = this.findRootTargets(selector);
            if (roots.isEmpty()) {
                continue;
            }
            if (selector.next() == null || FabricUtil.getCompatibility(this.context) < FabricUtil.COMPATIBILITY_0_17_5) {
                // Must ignore nested selectors to match prior behaviour
                if (selector.next() != null) {
                    String advice = MixinService.getService().getAdviceProvider().higherCompatibilityNeeded(
                            FabricUtil.COMPATIBILITY_0_17_5,
                            "0.17.5"
                    );
                    TargetSelectors.logger.warn(
                            "{} specifies a nested target selector which is being ignored at the current "
                                    + "compatibility version. Advice for the author: {}",
                            this.context, advice
                    );
                }
                selected.addAll(roots);
                continue;
            }
            this.scrapeLambdas();

            LinkedHashSet<ElementNode<?>> working = roots.stream()
                    .filter(root -> !this.lambdaMethods.contains(root))
                    .map(root -> ElementNode.of(this.targetClassNode, root))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            while (selector.next() != null) {
                int minDepth = selector.getMinRecurseDepth();
                int maxDepth = selector.getMaxRecurseDepth();
                selector = selector.next().configure(Configure.SELECT_LAMBDA);

                LinkedHashSet<ElementNode<?>> children = this.findNested(selector, working, minDepth, maxDepth);
                this.checkMinMatches(selector, children.size());
                working = children;
            }

            for (ElementNode<?> node : working) {
                selected.add(this.findMethod(node));
            }
        }

        this.submitTargets(selected);
    }

    /**
     * Recursively finds nested matches for the given selector in the given root
     * methods, depth-first.
     *
     * @param selector selector to be matched against the nested elements
     * @param parents root methods in which to search for lambdas
     * @param minDepth minimum relative nesting level to be matched
     * @param maxDepth maximum relative nesting level to be matched
     * @return matched elements
     */
    private LinkedHashSet<ElementNode<?>> findNested(
            ITargetSelector selector, Collection<ElementNode<?>> parents, int minDepth, int maxDepth
    ) {
        LinkedHashSet<ElementNode<?>> children = new LinkedHashSet<>();
        if (selector.getMaxMatchCount() <= 0) {
            return children;
        }

        for (ElementNode<?> parent : parents) {
            List<WithDepth<ElementNode<?>>> stack = new ArrayList<>();
            stack.add(new WithDepth<>(parent, 0));

            while (!stack.isEmpty()) {
                WithDepth<ElementNode<?>> current = stack.remove(stack.size() - 1);

                if (current.depth >= minDepth && selector.match(current.value).isExactMatch()) {
                    children.add(current.value);
                    if (children.size() == selector.getMaxMatchCount()) {
                        return children;
                    }
                }

                if (current.depth >= maxDepth) {
                    // Stop looking
                    continue;
                }

                for (ElementNode<?> lambda : Lists.reverse(this.containedLambdas.get(this.findMethod(current.value)))) {
                    stack.add(new WithDepth<>(lambda, current.depth + 1));
                }
            }
        }
        return children;
    }

    /**
     * Finds the root matches for the given selector in the target class
     * (ignores nested selectors).
     */
    private List<MethodNode> findRootTargets(ITargetSelector selector) {
        selector = selector.configure(Configure.SELECT_MEMBER);

        List<MethodNode> result = new ArrayList<>();
        int maxCount = selector.getMaxMatchCount();

        for (MethodNode target : this.targetClassNode.methods) {
            if (selector.match(ElementNode.of(this.targetClassNode, target)).isExactMatch()) {
                boolean isMixinMethod = Annotations.getVisible(target, MixinMerged.class) != null;
                if (maxCount <= 1 || ((this.isStatic || !Bytecode.isStatic(target)) && target != this.method && !isMixinMethod)) {
                    result.add(target);
                }

                if (result.size() >= maxCount) {
                    break;
                }
            }
        }

        this.checkMinMatches(selector, result.size());

        return result;
    }

    private void checkMinMatches(ITargetSelector selector, int matchCount) {
        if (matchCount < selector.getMinMatchCount()) {
            throw new InvalidInjectionException(this.context, new SelectorConstraintException(selector, String.format(
                    "Injection validation failed: %s for %s did not match the required number of targets (required=%d, matched=%d). %s%s",
                    selector, this.context.getElementDescription(), selector.getMinMatchCount(), matchCount,
                    this.mixin.getReferenceMapper().getStatus(), AnnotatedMethodInfo.getDynamicInfo(this.method))));
        }
    }

    private void submitTargets(Iterable<MethodNode> targets) {
        for (MethodNode target : targets) {
            this.checkTarget(target);
            this.targets.add(new SelectedMethod(target));
        }
    }

    private void checkTarget(MethodNode target) {
        AnnotationNode merged = Annotations.getVisible(target, MixinMerged.class);
        if (merged == null) {
            return;
        }
        
        if (Annotations.getVisible(target, Final.class) != null) {
            throw new InvalidInjectionException(this.context, String.format("%s cannot inject into @Final method %s::%s%s merged by %s", this,
                    this.mixin.getTargetClassName(), target.name, target.desc, Annotations.<String>getValue(merged, "mixin")));
        }
    }

    /**
     * If not done already, scrapes lambda information from all methods in the
     * target class, populating {@link containedLambdas} and
     * {@link lambdaMethods}.
     */
    private void scrapeLambdas() {
        if (this.scrapedLambdas) {
            return;
        }
        this.scrapedLambdas = true;

        for (MethodNode parent : this.targetClassNode.methods) {
            List<ElementNode<?>> lambdas = this.findContainedLambdas(parent);
            this.containedLambdas.put(parent, lambdas);
            for (ElementNode<?> lambda : lambdas) {
                this.lambdaMethods.add(this.findMethod(lambda));
            }
        }
    }

    private List<ElementNode<?>> findContainedLambdas(MethodNode parent) {
        List<ElementNode<?>> result = new ArrayList<>();
        for (ElementNode<?> candidate : ElementNode.lmfInsnList(parent.instructions)) {
            if (candidate == null) {
                continue;
            }
            if (!candidate.getImplOwner().equals(this.mixin.getTargetClassRef())) {
                // Reference to a foreign method
                continue;
            }

            MethodNode method = this.findMethod(candidate);

            if (Bytecode.hasFlag(method, Opcodes.ACC_SYNTHETIC) && !Bytecode.hasFlag(method, Opcodes.ACC_BRIDGE)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private MethodNode findMethod(ElementNode<?> node) {
        NameAndDesc candidate = new NameAndDesc(node.getImplName(), node.getImplDesc());
        MethodNode method = this.methodIndex.get(candidate);
        if (method == null) {
            throw new InvalidInjectionException(this.context, String.format(
                    "%s failed to find %s in target class %s",
                    this.context.getElementDescription(), candidate, this.mixin.getTargetClassRef()));
        }
        return method;
    }

    /**
     * Post-search validation that some targets were found, we can fail-fast if
     * no targets were actually identified or if the specified limits are
     * exceeded.
     * 
     * @param expectedCallbackCount Number of callbacks specified by expect
     * @param requiredCallbackCount Number of callbacks specified by require
     */
    public void validate(int expectedCallbackCount, int requiredCallbackCount) {
        int targetCount = this.targets.size(); 
        if (targetCount > 0) {
            return;
        }
        
        if ((this.mixin.getOption(Option.DEBUG_INJECTORS) && expectedCallbackCount > 0)) {
            throw new InvalidInjectionException(this.context,
                    String.format("Injection validation failed: %s could not find any targets matching %s in %s. %s%s", 
                            this.context.getElementDescription(), TargetSelectors.namesOf(this.selectors), this.mixin.getTargetClassRef(),
                            this.mixin.getReferenceMapper().getStatus(), AnnotatedMethodInfo.getDynamicInfo(this.method)));
        } else if (requiredCallbackCount > 0) {
            throw new InvalidInjectionException(this.context,
                    String.format("Critical injection failure: %s could not find any targets matching %s in %s. %s%s", 
                            this.context.getElementDescription(), TargetSelectors.namesOf(this.selectors), this.mixin.getTargetClassRef(),
                            this.mixin.getReferenceMapper().getStatus(), AnnotatedMethodInfo.getDynamicInfo(this.method)));
        }
    }

    /**
     * Print the names of the specified members as a human-readable list 
     * 
     * @param selectors members to print
     * @return human-readable list of member names
     */
    private static String namesOf(Collection<ITargetSelector> selectors) {
        int index = 0, count = selectors.size();
        StringBuilder sb = new StringBuilder();
        for (ITargetSelector selector : selectors) {
            if (index > 0) {
                if (index == (count - 1)) {
                    sb.append(" or ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append('\'').append(selector.toString()).append('\'');
            index++;
        }
        return sb.toString();
    }

    /**
     * DFS helper.
     */
    private static final class WithDepth<T> {
        public final T value;
        public final int depth;

        public WithDepth(T value, int depth) {
            this.value = value;
            this.depth = depth;
        }
    }

}
