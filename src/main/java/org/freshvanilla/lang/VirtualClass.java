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

import java.util.LinkedHashMap;
import java.util.Map;

public class VirtualClass<T> implements MetaClass<T> {
    private final MetaClass<T> metaClass;
    private final String nameWithParameters;
    private final MetaField<T, ?>[] fields;

    @SuppressWarnings("unchecked")
    public VirtualClass(MetaClass<T> metaClass, String nameWithParameters, String[] nameWithParametersSplit) {
        this.metaClass = metaClass;
        this.nameWithParameters = nameWithParameters;

        Map<String, MetaField<T, ?>> fieldsByName = new LinkedHashMap<String, MetaField<T, ?>>();
        for (MetaField<T, ?> field : metaClass.fields()) {
            fieldsByName.put(field.getName(), field);
        }

        this.fields = new MetaField[nameWithParametersSplit.length - 1];

        for (int i = 0; i < fields.length; i++) {
            MetaField<T, ?> field = fieldsByName.get(nameWithParametersSplit[i + 1]);
            if (field == null) {
                final String name = nameWithParametersSplit[i + 1];
                field = new VanillaField<T, Void>(name, null, Void.class);
            }
            fields[i] = field;
        }
    }

    public Class<T> getType() {
        return metaClass.getType();
    }

    public String nameWithParameters() {
        return nameWithParameters;
    }

    public MetaField<T, ?>[] fields() {
        return fields;
    }

    public T newInstance() throws InstantiationException {
        return metaClass.newInstance();
    }

    public boolean definesEquals() {
        return metaClass.definesEquals();
    }

    public Class<?> getComponentType() {
        return null;
    }

}
