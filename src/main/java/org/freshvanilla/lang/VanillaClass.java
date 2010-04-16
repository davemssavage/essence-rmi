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
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

import org.freshvanilla.lang.misc.Unsafe;

public class VanillaClass<T> implements MetaClass<T> {

    private final Class<T> clazz;
    private final String nameWithParameters;
    private final MetaField<T, ?>[] fields;
    private final boolean definesEquals;
    private final Class<?> componentType;

    public VanillaClass(Class<T> clazz) {
        this.clazz = clazz;

        fields = getFieldsForSerialization(clazz);

        StringBuilder sb = new StringBuilder(64);
        sb.append(clazz.getName());
        for (MetaField<T, ?> field : fields) {
            sb.append(',').append(field.getName());
        }

        nameWithParameters = sb.toString();
        definesEquals = clazz.getName().startsWith("java") || clazz.getName().startsWith("com.sun.");
        componentType = clazz.getComponentType();
    }

    @SuppressWarnings("unchecked")
    private static <T> MetaField<T, ?>[] getFieldsForSerialization(Class<?> clazz) {
        Map<String, MetaField<T, ?>> fieldMap = new LinkedHashMap<String, MetaField<T, ?>>();
        getFieldsForSerialization0(fieldMap, clazz);
        return fieldMap.values().toArray(new MetaField[fieldMap.size()]);
    }

    @SuppressWarnings("unchecked")
    private static <T> void getFieldsForSerialization0(Map<String, MetaField<T, ?>> fieldMap, Class<?> clazz) {
        if (clazz == null || clazz == Object.class) return;
        getFieldsForSerialization0(fieldMap, clazz.getSuperclass());
        for (Field field : clazz.getDeclaredFields()) {
            if ((field.getModifiers() & (Modifier.STATIC | Modifier.TRANSIENT)) != 0) continue;
            field.setAccessible(true);
            fieldMap.put(field.getName(), new VanillaField(field));
        }
    }

    public Class<T> getType() {
        return clazz;
    }

    public String nameWithParameters() {
        return nameWithParameters;
    }

    public MetaField<T, ?>[] fields() {
        return fields;
    }

    public T newInstance() throws InstantiationException {
        return Unsafe.newInstance(clazz);
    }

    public boolean definesEquals() {
        return definesEquals;
    }

    public Class<?> getComponentType() {
        return componentType;
    }
}
