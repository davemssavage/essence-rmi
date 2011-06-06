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

package org.freshvanilla.net;

import java.io.IOException;
import java.io.NotSerializableException;
import java.nio.ByteBuffer;

import org.freshvanilla.lang.MetaClass;
import org.freshvanilla.lang.MetaClasses;
import org.freshvanilla.lang.MetaField;

public class VanillaPojoSerializer implements PojoSerializer {

    private final MetaClasses _metaClasses;

    public VanillaPojoSerializer(MetaClasses metaclasses) {
        super();
        _metaClasses = metaclasses;
    }

    public <Pojo> boolean canSerialize(Pojo pojo) {
        final String className = pojo.getClass().getName();
        return !className.startsWith("java") && !className.startsWith("com.sun.")
               && !pojo.getClass().isArray();
    }

    @SuppressWarnings("unchecked")
    public <Pojo> void serialize(ByteBuffer wb, WireFormat wf, Pojo pojo) throws IOException {
        MetaClass<Pojo> clazz = _metaClasses.acquireMetaClass((Class<Pojo>)pojo.getClass());
        wf.writeTag(wb, clazz.nameWithParameters());

        for (MetaField<Pojo, ?> field : clazz.fields()) {
            wf.writeField(wb, field, pojo);
        }
    }

    public <Pojo> Pojo deserialize(ByteBuffer rb, WireFormat wf) throws IOException {
        String classWithParameters = (String)wf.readObject(rb);
        MetaClass<Pojo> clazz = _metaClasses.acquireMetaClass(classWithParameters);
        Pojo pojo;

        try {
            pojo = clazz.newInstance();
        }
        catch (InstantiationException e) {
            throw new NotSerializableException("Exception attempting to create " + clazz + ' ' + e);
        }

        for (MetaField<Pojo, ?> field : clazz.fields()) {
            wf.readField(rb, field, pojo);
        }

        return pojo;
    }

}
