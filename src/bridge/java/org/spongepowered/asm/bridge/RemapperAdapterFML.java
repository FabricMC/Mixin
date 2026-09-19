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
package org.spongepowered.asm.bridge;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

import org.spongepowered.asm.mixin.extensibility.IRemapper;

/**
 * Remapper adapter which remaps using FML's deobfuscating remapper
 */
public final class RemapperAdapterFML extends RemapperAdapter {
    
    private static final String DEOBFUSCATING_REMAPPER_CLASS = "fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper";
    private static final String DEOBFUSCATING_REMAPPER_CLASS_FORGE = "net.minecraftforge." + RemapperAdapterFML.DEOBFUSCATING_REMAPPER_CLASS;
    private static final String DEOBFUSCATING_REMAPPER_CLASS_LEGACY = "cpw.mods." + RemapperAdapterFML.DEOBFUSCATING_REMAPPER_CLASS;
    private static final String INSTANCE_FIELD = "INSTANCE";
    private static final String MAP_METHOD_NAME_METHOD = "mapMethodName";
    private static final String MAP_FIELD_NAME_METHOD = "mapFieldName";
    private static final String MAP_METHOD = "map";
    private static final String MAP_DESC_METHOD = "mapDesc";
    private static final String UNMAP_METHOD = "unmap";
    private static final MethodType MAP_METHOD_NAME_METHOD_TYPE = MethodType.methodType(String.class, String.class, String.class, String.class);
    private static final MethodType MAP_FIELD_NAME_METHOD_TYPE = MethodType.methodType(String.class, String.class, String.class, String.class);
    private static final MethodType MAP_METHOD_TYPE = MethodType.methodType(String.class, String.class);
    private static final MethodType MAP_DESC_METHOD_TYPE = MethodType.methodType(String.class, String.class);
    private static final MethodType UNMAP_METHOD_TYPE = MethodType.methodType(String.class, String.class);
    
    private final MethodHandle mhUnmap;
    
    private RemapperAdapterFML(MethodHandle mhMapMethodName, MethodHandle mhMapFieldName, MethodHandle mhMap, MethodHandle mhMapDesc, MethodHandle mhUnmap) {
        super(new org.objectweb.asm.commons.Remapper() {
            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                try {
                    return (String) mhMapMethodName.invokeExact(owner, name, descriptor);
                } catch (Throwable t) {
                    return name;
                }
            }
            
            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                try {
                    return (String) mhMapFieldName.invokeExact(owner, name, descriptor);
                } catch (Throwable t) {
                    return name;
                }
            }
            
            @Override
            public String map(String internalName) {
                try {
                    return (String) mhMap.invokeExact(internalName);
                } catch (Throwable t) {
                    return internalName;
                }
            }
            
            @Override
            public String mapDesc(String descriptor) {
                try {
                    return (String) mhMapDesc.invokeExact(descriptor);
                } catch (Throwable t) {
                    return descriptor;
                }
            }
        });
        this.logger.info("Initialised Mixin FML Remapper Adapter with {}", remapper);
        this.mhUnmap = mhUnmap;
    }

    @Override
    public String unmap(String typeName) {
        try {
            return (String) this.mhUnmap.invokeExact(typeName);
        } catch (Throwable t) {
            return typeName;
        }
    }
    
    /**
     * Factory method
     */
    public static IRemapper create() {
        try {
            Lookup lookup = MethodHandles.publicLookup();
            Class<?> clDeobfRemapper = RemapperAdapterFML.getFMLDeobfuscatingRemapper();
            Field singletonField = clDeobfRemapper.getDeclaredField(RemapperAdapterFML.INSTANCE_FIELD);
            Object fmlRemapper = singletonField.get(null);
            MethodHandle mhMapMethodName = lookup.bind(fmlRemapper, MAP_METHOD_NAME_METHOD, MAP_METHOD_NAME_METHOD_TYPE);
            MethodHandle mhMapFieldName = lookup.bind(fmlRemapper, MAP_FIELD_NAME_METHOD, MAP_FIELD_NAME_METHOD_TYPE);
            MethodHandle mhMap = lookup.bind(fmlRemapper, MAP_METHOD, MAP_METHOD_TYPE);
            MethodHandle mhMapDesc = lookup.bind(fmlRemapper, MAP_DESC_METHOD, MAP_DESC_METHOD_TYPE);
            MethodHandle mhUnmap = lookup.bind(fmlRemapper, UNMAP_METHOD, UNMAP_METHOD_TYPE);
            return new RemapperAdapterFML(mhMapMethodName, mhMapFieldName, mhMap, mhMapDesc, mhUnmap);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Attempt to get the FML Deobfuscating Remapper, tries the post-1.8
     * namespace first and falls back to 1.7.10 if class lookup fails
     */
    private static Class<?> getFMLDeobfuscatingRemapper() throws ClassNotFoundException {
        try {
            return Class.forName(RemapperAdapterFML.DEOBFUSCATING_REMAPPER_CLASS_FORGE);
        } catch (ClassNotFoundException ex) {
            return Class.forName(RemapperAdapterFML.DEOBFUSCATING_REMAPPER_CLASS_LEGACY);
        }
    }

}
