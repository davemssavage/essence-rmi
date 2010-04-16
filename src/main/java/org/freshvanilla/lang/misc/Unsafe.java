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
package org.freshvanilla.lang.misc;

import java.lang.reflect.Field;

public class Unsafe {
    private static final sun.misc.Unsafe unsafe;

    private Unsafe() {
        // not used
    }

    static {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (sun.misc.Unsafe) field.get(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static AssertionError rethrow(Throwable thrown) {
        unsafe.throwException(thrown);
        throw new AssertionError("Should not get here.");
    }

    @SuppressWarnings("unchecked")
    public static <T> T newInstance(Class<T> clazz) throws InstantiationException {
        return (T) unsafe.allocateInstance(clazz);
    }

    public interface FieldAccessor<T> {
        public <Pojo> T getField(Pojo pojo);

        public <Pojo> boolean getBoolean(Pojo pojo);

        public <Pojo> long getNum(Pojo pojo);

        public <Pojo> double getDouble(Pojo pojo);

        public <Pojo> void setField(Pojo pojo, T value);

        public <Pojo> void setBoolean(Pojo pojo, boolean value);

        public <Pojo> void setNum(Pojo pojo, long value);

        public <Pojo> void setDouble(Pojo pojo, double value);
    }

    @SuppressWarnings("unchecked")
    public static FieldAccessor getFieldAccessor(Field field) {
        Class<?> type = field.getType();
        final long offset = unsafe.objectFieldOffset(field);

        if (type == boolean.class) {
            return new BooleanFieldAccessor(offset);
        }

        if (type == byte.class) {
            return new ByteFieldAccessor(offset);
        }

        if (type == char.class) {
            return new CharFieldAccessor(offset);
        }

        if (type == short.class) {
            return new ShortFieldAccessor(offset);
        }

        if (type == int.class) {
            return new IntFieldAccessor(offset);
        }

        if (type == float.class) {
            return new FloatFieldAccessor(offset);
        }

        if (type == long.class) {
            return new LongFieldAccessor(offset);
        }

        if (type == double.class) {
            return new DoubleFieldAccessor(offset);
        }

        return new ObjectFieldAccessor(offset);
    }

    static class BooleanFieldAccessor implements FieldAccessor<Boolean> {
        private final long offset;

        BooleanFieldAccessor(long offset) {
            this.offset = offset;
        }

        public <Pojo> Boolean getField(Pojo pojo) {
            return unsafe.getBoolean(pojo, offset);
        }

        public <Pojo> boolean getBoolean(Pojo pojo) {
            return unsafe.getBoolean(pojo, offset);
        }

        public <Pojo> long getNum(Pojo pojo) {
            return unsafe.getBoolean(pojo, offset) ? 1 : 0;
        }

        public <Pojo> double getDouble(Pojo pojo) {
            return unsafe.getBoolean(pojo, offset) ? 1 : 0;
        }

        public <Pojo> void setField(Pojo pojo, Boolean object) {
            unsafe.putBoolean(pojo, offset, object);
        }

        public <Pojo> void setBoolean(Pojo pojo, boolean flag) {
            unsafe.putBoolean(pojo, offset, flag);
        }

        public <Pojo> void setNum(Pojo pojo, long value) {
            unsafe.putBoolean(pojo, offset, value != 0);
        }

        public <Pojo> void setDouble(Pojo pojo, double value) {
            unsafe.putBoolean(pojo, offset, value != 0);
        }
    }

    static class ByteFieldAccessor implements FieldAccessor<Byte> {
        private final long offset;

        ByteFieldAccessor(long offset) {
            this.offset = offset;
        }

        public <Pojo> Byte getField(Pojo pojo) {
            return unsafe.getByte(pojo, offset);
        }

        public <Pojo> boolean getBoolean(Pojo pojo) {
            return unsafe.getByte(pojo, offset) != 0;
        }

        public <Pojo> long getNum(Pojo pojo) {
            return unsafe.getByte(pojo, offset);
        }

        public <Pojo> double getDouble(Pojo pojo) {
            return unsafe.getByte(pojo, offset);
        }

        public <Pojo> void setField(Pojo pojo, Byte object) {
            unsafe.putByte(pojo, offset, object);
        }

        public <Pojo> void setBoolean(Pojo pojo, boolean flag) {
            unsafe.putByte(pojo, offset, (byte) (flag ? 1 : 0));
        }

        public <Pojo> void setNum(Pojo pojo, long value) {
            unsafe.putByte(pojo, offset, (byte) value);
        }

        public <Pojo> void setDouble(Pojo pojo, double value) {
            unsafe.putByte(pojo, offset, (byte) value);
        }
    }

    static class CharFieldAccessor implements FieldAccessor<Character> {
        private final long offset;

        CharFieldAccessor(long offset) {
            this.offset = offset;
        }

        public <Pojo> Character getField(Pojo pojo) {
            return unsafe.getChar(pojo, offset);
        }

        public <Pojo> boolean getBoolean(Pojo pojo) {
            return unsafe.getChar(pojo, offset) != 0;
        }

        public <Pojo> long getNum(Pojo pojo) {
            return unsafe.getChar(pojo, offset);
        }

        public <Pojo> double getDouble(Pojo pojo) {
            return unsafe.getChar(pojo, offset);
        }

        public <Pojo> void setField(Pojo pojo, Character value) {
            unsafe.putChar(pojo, offset, value);
        }

        public <Pojo> void setBoolean(Pojo pojo, boolean value) {
            unsafe.putChar(pojo, offset, (char) (value ? 1 : 0));
        }

        public <Pojo> void setNum(Pojo pojo, long value) {
            unsafe.putChar(pojo, offset, (char) value);
        }

        public <Pojo> void setDouble(Pojo pojo, double value) {
            unsafe.putChar(pojo, offset, (char) value);
        }
    }

    static class ShortFieldAccessor implements FieldAccessor<Short> {
        private final long offset;

        ShortFieldAccessor(long offset) {
            this.offset = offset;
        }

        public <Pojo> Short getField(Pojo pojo) {
            return unsafe.getShort(pojo, offset);
        }

        public <Pojo> boolean getBoolean(Pojo pojo) {
            return unsafe.getShort(pojo, offset) != 0;
        }

        public <Pojo> long getNum(Pojo pojo) {
            return unsafe.getShort(pojo, offset);
        }

        public <Pojo> double getDouble(Pojo pojo) {
            return unsafe.getShort(pojo, offset);
        }

        public <Pojo> void setField(Pojo pojo, Short value) {
            unsafe.putShort(pojo, offset, value);
        }

        public <Pojo> void setBoolean(Pojo pojo, boolean value) {
            unsafe.putShort(pojo, offset, (short) (value ? 1 : 0));
        }

        public <Pojo> void setNum(Pojo pojo, long value) {
            unsafe.putShort(pojo, offset, (short) value);
        }

        public <Pojo> void setDouble(Pojo pojo, double value) {
            unsafe.putShort(pojo, offset, (short) value);
        }
    }

    static class IntFieldAccessor implements FieldAccessor<Integer> {
        private final long offset;

        IntFieldAccessor(long offset) {
            this.offset = offset;
        }

        public <Pojo> Integer getField(Pojo pojo) {
            return unsafe.getInt(pojo, offset);
        }

        public <Pojo> boolean getBoolean(Pojo pojo) {
            return unsafe.getInt(pojo, offset) != 0;
        }

        public <Pojo> long getNum(Pojo pojo) {
            return unsafe.getInt(pojo, offset);
        }

        public <Pojo> double getDouble(Pojo pojo) {
            return unsafe.getInt(pojo, offset);
        }

        public <Pojo> void setField(Pojo pojo, Integer value) {
            unsafe.putInt(pojo, offset, value);
        }

        public <Pojo> void setBoolean(Pojo pojo, boolean value) {
            unsafe.putInt(pojo, offset, value ? 1 : 0);
        }

        public <Pojo> void setNum(Pojo pojo, long value) {
            unsafe.putInt(pojo, offset, (int) value);
        }

        public <Pojo> void setDouble(Pojo pojo, double value) {
            unsafe.putInt(pojo, offset, (int) value);
        }
    }

    static class FloatFieldAccessor implements FieldAccessor<Float> {
        private final long offset;

        FloatFieldAccessor(long offset) {
            this.offset = offset;
        }

        public <Pojo> Float getField(Pojo pojo) {
            return unsafe.getFloat(pojo, offset);
        }

        public <Pojo> boolean getBoolean(Pojo pojo) {
            return unsafe.getFloat(pojo, offset) != 0;
        }

        public <Pojo> long getNum(Pojo pojo) {
            return (long) unsafe.getFloat(pojo, offset);
        }

        public <Pojo> double getDouble(Pojo pojo) {
            return unsafe.getFloat(pojo, offset);
        }

        public <Pojo> void setField(Pojo pojo, Float value) {
            unsafe.putFloat(pojo, offset, value);
        }

        public <Pojo> void setBoolean(Pojo pojo, boolean value) {
            unsafe.putFloat(pojo, offset, value ? 1.0f : 0.0f);
        }

        public <Pojo> void setNum(Pojo pojo, long value) {
            unsafe.putFloat(pojo, offset, value);
        }

        public <Pojo> void setDouble(Pojo pojo, double value) {
            unsafe.putFloat(pojo, offset, (float) value);
        }
    }

    static class LongFieldAccessor implements FieldAccessor<Long> {
        private final long offset;

        LongFieldAccessor(long offset) {
            this.offset = offset;
        }

        public <Pojo> Long getField(Pojo pojo) {
            return unsafe.getLong(pojo, offset);
        }

        public <Pojo> boolean getBoolean(Pojo pojo) {
            return unsafe.getLong(pojo, offset) != 0;
        }

        public <Pojo> long getNum(Pojo pojo) {
            return unsafe.getLong(pojo, offset);
        }

        public <Pojo> double getDouble(Pojo pojo) {
            return unsafe.getLong(pojo, offset);
        }

        public <Pojo> void setField(Pojo pojo, Long value) {
            unsafe.putLong(pojo, offset, value);
        }

        public <Pojo> void setBoolean(Pojo pojo, boolean value) {
            unsafe.putLong(pojo, offset, value ? 1L : 0L);
        }

        public <Pojo> void setNum(Pojo pojo, long value) {
            unsafe.putLong(pojo, offset, value);
        }

        public <Pojo> void setDouble(Pojo pojo, double value) {
            unsafe.putLong(pojo, offset, (long) value);
        }
    }

    static class DoubleFieldAccessor implements FieldAccessor<Double> {
        private final long offset;

        DoubleFieldAccessor(long offset) {
            this.offset = offset;
        }

        public <Pojo> Double getField(Pojo pojo) {
            return unsafe.getDouble(pojo, offset);
        }

        public <Pojo> boolean getBoolean(Pojo pojo) {
            return unsafe.getDouble(pojo, offset) != 0;
        }

        public <Pojo> long getNum(Pojo pojo) {
            return (long) unsafe.getDouble(pojo, offset);
        }

        public <Pojo> double getDouble(Pojo pojo) {
            return unsafe.getDouble(pojo, offset);
        }

        public <Pojo> void setField(Pojo pojo, Double value) {
            unsafe.putDouble(pojo, offset, value);
        }

        public <Pojo> void setBoolean(Pojo pojo, boolean value) {
            unsafe.putDouble(pojo, offset, value ? 1.0 : 0.0);
        }

        public <Pojo> void setNum(Pojo pojo, long value) {
            unsafe.putDouble(pojo, offset, value);
        }

        public <Pojo> void setDouble(Pojo pojo, double value) {
            unsafe.putDouble(pojo, offset, value);
        }
    }

    static class ObjectFieldAccessor implements FieldAccessor<Object> {
        private final long offset;

        ObjectFieldAccessor(long offset) {
            this.offset = offset;
        }

        public <Pojo> Object getField(Pojo pojo) {
            return unsafe.getObject(pojo, offset);
        }

        public <Pojo> boolean getBoolean(Pojo pojo) {
            return Boolean.TRUE.equals(unsafe.getObject(pojo, offset));
        }

        public <Pojo> long getNum(Pojo pojo) {
            Object obj = unsafe.getObject(pojo, offset);
            if (obj instanceof Number)
                return ((Number) obj).longValue();
            throw new AssertionError("Cannot convert " + obj + " to long.");
        }

        public <Pojo> double getDouble(Pojo pojo) {
            Object obj = unsafe.getObject(pojo, offset);
            if (obj instanceof Number)
                return ((Number) obj).doubleValue();
            throw new AssertionError("Cannot convert " + obj + " to double.");
        }

        public <Pojo> void setField(Pojo pojo, Object value) {
            unsafe.putObject(pojo, offset, value);
        }

        public <Pojo> void setBoolean(Pojo pojo, boolean value) {
            unsafe.putObject(pojo, offset, value);
        }

        public <Pojo> void setNum(Pojo pojo, long value) {
            unsafe.putObject(pojo, offset, value);
        }

        public <Pojo> void setDouble(Pojo pojo, double value) {
            unsafe.putObject(pojo, offset, value);
        }
    }
}
