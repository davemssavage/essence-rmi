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
import java.io.StreamCorruptedException;
import java.nio.ByteBuffer;

import org.freshvanilla.lang.MetaField;

public interface WireFormat {

    public void flush(DataSocket ds, ByteBuffer writeBuffer) throws IOException;

    public Object[] readArray(ByteBuffer rb) throws ClassNotFoundException, IOException;

    public boolean readBoolean(ByteBuffer readBuffer) throws StreamCorruptedException;

    public double readDouble(ByteBuffer rb) throws StreamCorruptedException;

    public <Pojo, T> void readField(ByteBuffer rb, MetaField<Pojo, T> field, Pojo pojo)
        throws ClassNotFoundException, IOException;

    public long readNum(ByteBuffer readBuffer) throws StreamCorruptedException;

    public Object readObject(ByteBuffer readBuffer) throws ClassNotFoundException, IOException;

    public String readString(ByteBuffer readBuffer) throws ClassNotFoundException, IOException;

    public void writeArray(ByteBuffer writeBuffer, int maxLength, Object... objects) throws IOException;

    public void writeBoolean(ByteBuffer readBuffer, boolean flag);

    public <Pojo, T> void writeField(ByteBuffer wb, MetaField<Pojo, T> field, Pojo pojo) throws IOException;

    public void writeNum(ByteBuffer writeBuffer, long value);

    public void writeObject(ByteBuffer writeBuffer, Object object) throws IOException;

    public void writeTag(ByteBuffer writeBuffer, String tag);

}
