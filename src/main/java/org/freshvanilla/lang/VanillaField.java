/*
 Copyright 2008 Peter Lawrey

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an &quot;AS IS&quot; BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.freshvanilla.lang;

import java.lang.reflect.Field;

import org.freshvanilla.lang.misc.Unsafe;
import org.freshvanilla.lang.misc.Unsafe.FieldAccessor;

public class VanillaField<D, T> implements MetaField<D, T> {
    private final FieldAccessor<T> accessor;
    private final boolean primitive;
    private final Class<T> type;
    private final String name;

    @SuppressWarnings("unchecked")
    public VanillaField(Field field) {
        this(field.getName(), Unsafe.getFieldAccessor(field), (Class<T>)field.getType());
    }

    VanillaField(String name, FieldAccessor<T> accessor, Class<T> type) {
        this.name = name;
        this.accessor = accessor;
        this.type = type;
        primitive = MetaClasses.isPrimitive(type);
    }

    public String getName() {
        return name;
    }

    public void set(D pojo, T value) {
        accessor.setField(pojo, value);
    }

    public T get(D pojo) {
        return accessor.getField(pojo);
    }

    public boolean isPrimitive() {
        return primitive;
    }

    public Class<T> getType() {
        return type;
    }

    public void setBoolean(D pojo, boolean flag) {
        accessor.setBoolean(pojo, flag);
    }

    public boolean getBoolean(D pojo) {
        return accessor.getBoolean(pojo);
    }

    public void setNum(D pojo, long value) {
        accessor.setNum(pojo, value);
    }

    public long getNum(D pojo) {
        return accessor.getNum(pojo);
    }

    public void setDouble(D pojo, double value) {
        accessor.setDouble(pojo, value);
    }

    public double getDouble(D pojo) {
        return accessor.getDouble(pojo);
    }

    @Override
    public String toString() {
        return name + ':' + type.getName();
    }
}
