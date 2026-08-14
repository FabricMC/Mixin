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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.spongepowered.asm.logging.ILogger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.throwables.CompanionPluginError;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.MixinService;

import com.google.common.base.Strings;

/**
 * Convenience wrapper for mixin config plugins
 */
class PluginHandle {
    
    private static final ILogger logger = MixinService.getService().getLogger("mixin");

    /**
     * Parent config which owns this plugin handle
     */
    private final MixinConfig parent;

    /**
     * Plugin instance, can be null
     */
    private final IMixinConfigPlugin plugin;

    /**
     * <p>Keeps track of whether an exception was encountered
     * when trying to run {@link #applyLegacy(Method, String, ClassNode, String, IMixinInfo)} if legacy methods were
     * found.</p>
     *
     * @see #mdPreApply
     * @see #mdPostApply
     * @see #findLegacyApply(Class, String)
     * @see #applyLegacy(Method, String, ClassNode, String, IMixinInfo)
     */
    private boolean hasFailedLegacyApply = false;
    
    /**
     * <p>Reflection objects for calling legacy (pre 0.8) preApply and postApply. May be {@code null} when no legacy method
     * should be called.</p>
     *
     * @see #findLegacyApply(Class, String)
     */
    private final Method mdPreApply, mdPostApply;

    PluginHandle(MixinConfig parent, IMixinService service, String pluginClassName) {
        IMixinConfigPlugin plugin = null;
        Method mdPreApply = null;
        Method mdPostApply = null;
        
        if (!Strings.isNullOrEmpty(pluginClassName)) {
            try {
                Class<?> pluginClass = service.getClassProvider().findClass(pluginClassName, true);
                plugin = (IMixinConfigPlugin)pluginClass.getDeclaredConstructor().newInstance();
                mdPreApply = findLegacyApply(pluginClass, "preApply");
                mdPostApply = findLegacyApply(pluginClass, "postApply");
            } catch (Throwable th) {
                PluginHandle.logger.error("Error loading companion plugin class [{}] for mixin config [{}]. The plugin may be out of date: {}:{}",
                        pluginClassName, parent, th.getClass().getSimpleName(), th.getMessage(), th);
                plugin = null;
                mdPreApply = null;
                mdPostApply = null;
            }
        }
        
        this.parent = parent;
        this.plugin = plugin;
        this.mdPreApply = mdPreApply;
        this.mdPostApply = mdPostApply;
    }

    /**
     * <p>Called during construction to populate {@link #mdPreApply} and {@link #mdPostApply} to handle legacy (pre 0.8) config plugins.</p>
     *
     * @param pluginClass The implementing class of this {@link #plugin}
     * @param applyName Either {@code "preApply"} or {@code "postApply"} depending on which legacy method to check for.
     * @return {@code null} if no legacy method should be used or if none is found, otherwise the legacy method object to invoke.
     *
     * @see #mdPreApply
     * @see #mdPostApply
     */
    private static Method findLegacyApply(Class<?> pluginClass, String applyName) {
        Method legacyMethod = null;
        try {
            Method nonLegacy = pluginClass.getMethod(applyName, String.class, org.objectweb.asm.tree.ClassNode.class, String.class, IMixinInfo.class);

            // Check if we found the interface's default rather than an override, and look for a fallback in the former case.
            if (nonLegacy.getDeclaringClass() == IMixinConfigPlugin.class) {
                try {
                    legacyMethod = pluginClass.getMethod(applyName, String.class, org.spongepowered.asm.lib.tree.ClassNode.class, String.class, IMixinInfo.class);
                } catch (NoSuchMethodException ignored) {
                    // No legacy method found, nothing to do
                }
            }
        } catch (Exception unexpectedEx) {
            throw new CompanionPluginError("Encountered an unexpected error when trying to resolve method " + applyName + "or its legacy fallback.", unexpectedEx);
        }

        return legacyMethod;
    }

    IMixinConfigPlugin get() {
        return this.plugin;
    }
    
    boolean isAvailable() {
        return this.plugin != null;
    }

    void onLoad(String mixinPackage) {
        if (this.plugin != null) {
            this.plugin.onLoad(mixinPackage);
        }
    }

    String getRefMapperConfig() {
        return this.plugin != null ? this.plugin.getRefMapperConfig() : null;
    }

    List<String> getMixins() {
        return this.plugin != null ? this.plugin.getMixins() : null;
    }

    boolean shouldApplyMixin(String targetName, String className) {
        return this.plugin == null || this.plugin.shouldApplyMixin(targetName, className);
    }
    
    /**
     * Called immediately before the mixin is applied to targetClass
     */
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, MixinInfo mixinInfo) throws Exception {
        if (this.plugin == null) {
            return;
        }
        
        if (this.hasFailedLegacyApply) {
            throw new IllegalStateException("Companion plugin failure for [" + this.parent + "] plugin [" + this.plugin.getClass() + "]");
        }
        
        if (this.mdPreApply != null) {
            try {
                this.applyLegacy(this.mdPreApply, targetClassName, targetClass, mixinClassName, mixinInfo);
            } catch (Exception ex) {
                this.hasFailedLegacyApply = true;
                throw ex;
            }
            return;
        } 

        this.plugin.preApply(targetClassName, targetClass, mixinClassName, mixinInfo);
    }

    /**
     * Called immediately after the mixin is applied to targetClass
     */
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, MixinInfo mixinInfo) throws Exception {
        if (this.plugin == null) {
            return;
        }
        
        if (this.hasFailedLegacyApply) {
            throw new IllegalStateException("Companion plugin failure for [" + this.parent + "] plugin [" + this.plugin.getClass() + "]");
        }
        
        if (this.mdPostApply != null) {
            try {
                this.applyLegacy(this.mdPostApply, targetClassName, targetClass, mixinClassName, mixinInfo);
            } catch (Exception ex) {
                this.hasFailedLegacyApply = true;
                throw ex;
            }
            return;
        } 

        this.plugin.postApply(targetClassName, targetClass, mixinClassName, mixinInfo);
    }

    private void applyLegacy(Method method, String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        try {
            method.invoke(this.plugin, targetClassName, new org.spongepowered.asm.lib.tree.ClassNode(targetClass), mixinClassName, mixinInfo);
        } catch (LinkageError err) {
            throw new CompanionPluginError(this.apiError("Accessing [" + err.getMessage() + "]"), err);
        } catch (IllegalAccessException ex) {
            throw new CompanionPluginError(this.apiError("Fallback failed [" + ex.getMessage() + "]"), ex);
        } catch (IllegalArgumentException ex) {
            throw new CompanionPluginError(this.apiError("Fallback failed [" + ex.getMessage() + "]"), ex);
        } catch (InvocationTargetException ex) {
            Throwable th = ex.getCause() != null ? ex.getCause() : ex;
            throw new CompanionPluginError(this.apiError("Fallback failed [" + th.getMessage() + "]"), th);
        }
    }

    private String apiError(String message) {
        return String.format("Companion plugin attempted to use a deprected API in [%s] plugin [%s]: %s",
                this.parent, this.plugin.getClass().getName(), message);
    }

}
