/*
 Copyright 2008-2011 the original author or authors

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

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings({"unchecked", "rawtypes"})
public class MetaClasses {

    private static final Set<Class<?>> PRIMITIVES = new HashSet<Class<?>>(
        Arrays.asList(new Class<?>[]{boolean.class, byte.class, char.class, short.class, int.class,
            float.class, double.class, long.class}));

    private final ConcurrentMap<String, MetaClass> NAME_META_CLASS_MAP = new ConcurrentHashMap<String, MetaClass>();
    private final ConcurrentMap<Class<?>, MetaClass> META_CLASS_MAP = new ConcurrentHashMap<Class<?>, MetaClass>();
    private final ConcurrentMap<String, Throwable> NO_CLASS_SET = new ConcurrentHashMap<String, Throwable>();

    private final ClassLoader _classLoader;

    public static boolean isPrimitive(Class<?> clazz) {
        return PRIMITIVES.contains(clazz);
    }

    public static <Pojo1, Pojo2> boolean equals(MetaClasses metaclasses, Pojo1 pojo1, Pojo2 pojo2) {
        if (pojo1 == null) {
            return pojo2 == null;
        }

        if (pojo2 == null) {
            return false;
        }

        if (pojo1 == pojo2) {
            return true;
        }

        final Class<Pojo1> pojoClass = (Class<Pojo1>)pojo1.getClass();
        final MetaClass<Pojo1> metaClass = metaclasses.acquireMetaClass(pojoClass);

        if (metaClass.definesEquals()) {
            return pojo1.equals(pojo2);
        }

        if (pojoClass != pojo2.getClass()) {
            return false;
        }

        for (MetaField field : metaClass.fields()) {
            if (!equals(metaclasses, field.get(pojo1), field.get(pojo2))) {
                return false;
            }
        }

        return true;
    }

    public static <Pojo> Map<String, Object> asMap(MetaClasses metaclasses, Pojo pojo) {
        if (pojo == null) {
            return null;
        }

        MetaClass<Pojo> metaClass = metaclasses.acquireMetaClass((Class<Pojo>)pojo.getClass());
        MetaField<Pojo, ?>[] metaFields = metaClass.fields();
        Map<String, Object> ret = new LinkedHashMap<String, Object>(metaFields.length * 2);

        for (MetaField<Pojo, ?> field : metaFields) {
            ret.put(field.getName(), field.get(pojo));
        }

        return ret;
    }

    public static <Pojo> String asString(MetaClasses metaclasses, Pojo pojo) {
        if (pojo == null) {
            return "null";
        }

        return pojo.getClass().getSimpleName() + ' ' + asMap(metaclasses, pojo);
    }

    public static <Pojo> int hashCodeFor(MetaClasses metaclasses, Pojo pojo) {
        if (pojo == null) {
            return 0;
        }

        int hash = 0;
        MetaClass<Pojo> metaClass = metaclasses.acquireMetaClass((Class<Pojo>)pojo.getClass());

        for (MetaField field : metaClass.fields()) {
            int fieldHash = field.getName().hashCode();

            if (fieldHash == 0) {
                fieldHash = 101;
            }

            final Object obj = field.get(pojo);
            if (obj != null) {
                hash += fieldHash * obj.hashCode();
            }
        }

        return hash;
    }

    public MetaClasses(ClassLoader classLoader) {
        super();
        _classLoader = classLoader;
    }

    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return _classLoader.loadClass(name);
    }

    public <T> MetaClass<T> acquireMetaClass(String classDescription) {
        MetaClass<T> metaClass = NAME_META_CLASS_MAP.get(classDescription);
        if (metaClass == null) {
            if (NO_CLASS_SET.containsKey(classDescription)) {
                return null;
            }

            String[] parts = classDescription.split(",");
            String clazz2 = parts[0];

            try {
                metaClass = acquireMetaClass((Class)_classLoader.loadClass(clazz2));
                MetaField<T, ?>[] metaFields = metaClass.fields();
                boolean okay = false;

                if (metaFields.length == parts.length - 1) {
                    okay = true;
                    for (int i = 0; i < metaFields.length; i++) {
                        MetaField<T, ?> field = metaFields[i];
                        if (!field.getName().equals(parts[i + 1])) {
                            okay = false;
                            break;
                        }
                    }
                }

                if (!okay) {
                    metaClass = new VirtualClass(metaClass, classDescription, parts);
                }

                MetaClass<T> prev = NAME_META_CLASS_MAP.putIfAbsent(classDescription, metaClass);
                if (prev != null) {
                    metaClass = prev;
                }
            }
            catch (ClassNotFoundException e) {
                NO_CLASS_SET.putIfAbsent(classDescription, e);
            }
        }
        return metaClass;
    }

    public <T> MetaClass<T> acquireMetaClass(Class<T> aClass) {
        MetaClass<T> vanillaClass = META_CLASS_MAP.get(aClass);
        if (vanillaClass == null) {
            vanillaClass = new VanillaClass<T>(aClass);
            MetaClass<T> prev = META_CLASS_MAP.putIfAbsent(aClass, vanillaClass);
            if (prev != null) {
                vanillaClass = prev;
            }
        }
        return vanillaClass;
    }

}
