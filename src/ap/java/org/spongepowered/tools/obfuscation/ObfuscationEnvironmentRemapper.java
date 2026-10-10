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

package org.spongepowered.tools.obfuscation;

import org.spongepowered.asm.mixin.extensibility.IRemapper;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;
import org.spongepowered.asm.obfuscation.mapping.common.MappingField;
import org.spongepowered.asm.obfuscation.mapping.common.MappingMethod;
import org.spongepowered.tools.obfuscation.interfaces.IObfuscationDataProvider;

class ObfuscationEnvironmentRemapper implements IRemapper {
    private final ObfuscationEnvironment env;
    private final IObfuscationDataProvider dataProvider;

    public ObfuscationEnvironmentRemapper(ObfuscationEnvironment env, IObfuscationDataProvider dataProvider) {
        this.env = env;
        this.dataProvider = dataProvider;
    }

    @Override
    public String mapMethodName(String owner, String name, String desc) {
        MappingMethod remapped = this.dataProvider.getObfMethodRecursive(new MemberInfo(name, owner, desc)).get(this.env.getType());
        if (remapped != null) {
            return remapped.getSimpleName();
        }
        return name;
    }

    @Override
    public String mapFieldName(String owner, String name, String desc) {
        MappingField remapped = this.dataProvider.getObfFieldRecursive(new MemberInfo(name, owner, desc)).get(this.env.getType());
        if (remapped != null) {
            return remapped.getSimpleName();
        }
        return name;
    }

    @Override
    public String map(String typeName) {
        String remapped = this.dataProvider.getObfClass(typeName).get(this.env.getType());
        if (remapped != null) {
            return remapped;
        }
        return typeName;
    }

    @Override
    public String unmap(String typeName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String mapDesc(String desc) {
        return this.env.remapDescriptor(desc);
    }

    @Override
    public String unmapDesc(String desc) {
        throw new UnsupportedOperationException();
    }
}
