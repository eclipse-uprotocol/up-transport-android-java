/*
 * Copyright (c) 2024 General Motors GTO LLC
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * SPDX-FileType: SOURCE
 * SPDX-FileCopyrightText: 2023 General Motors GTO LLC
 * SPDX-License-Identifier: Apache-2.0
 */
package org.eclipse.uprotocol.v1.internal;

import static org.eclipse.uprotocol.v1.internal.ParcelableMessage.VALUE_NOT_SET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.protobuf.ByteString;
import com.google.protobuf.Value;
import com.google.protobuf.Value.KindCase;

import org.eclipse.uprotocol.TestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ParcelableMessageTest extends TestBase {
    private static final Value VALUE = Value.newBuilder().setBoolValue(true).build();
    private static final Value VALUE2 = Value.newBuilder().setNumberValue(1).build();
    private static final ParcelableValue PARCELABLE_VALUE = new ParcelableValue(VALUE);
    private static final ByteString STRING = ByteString.copyFromUtf8("Test");
    private Parcel mParcel;

    @SuppressWarnings("NewClassNamingConvention")
    private static class ParcelableValue extends ParcelableMessage<Value> {

        public static final Creator<ParcelableValue> CREATOR = new Creator<>() {
            public ParcelableValue createFromParcel(Parcel in) {
                return new ParcelableValue(in);
            }

            public ParcelableValue[] newArray(int size) {
                return new ParcelableValue[size];
            }
        };

        private ParcelableValue(@NonNull Parcel in) {
            super(in);
        }

        public ParcelableValue(@NonNull Value value) {
            super(value);
        }

        @Override
        protected void writeMessage(@NonNull Parcel out, int flags) {
            final KindCase kindCase = mMessage.getKindCase();
            out.writeInt(kindCase.getNumber());
            if (kindCase == KindCase.BOOL_VALUE) {
                out.writeBoolean(mMessage.getBoolValue());
            }
        }

        @Override
        protected @NonNull Value readMessage(@NonNull Parcel in) {
            final Value.Builder builder = Value.newBuilder();
            final KindCase kindCase = KindCase.forNumber(in.readInt());
            if (kindCase == KindCase.BOOL_VALUE) {
                builder.setBoolValue(in.readBoolean());
            }
            return builder.build();
        }
    }

    @SuppressWarnings("NewClassNamingConvention")
    private static class ParcelableValueEx extends ParcelableValue {

        public static final Creator<ParcelableValue> CREATOR = new Creator<>() {
            public ParcelableValueEx createFromParcel(Parcel in) {
                return new ParcelableValueEx(in);
            }

            public ParcelableValueEx[] newArray(int size) {
                return new ParcelableValueEx[size];
            }
        };

        private ParcelableValueEx(@NonNull Parcel in) {
            super(in);
        }

        public ParcelableValueEx(@NonNull Value value) {
            super(value);
        }

        @Override
        protected void writeMessage(@NonNull Parcel out, int flags) {
            final KindCase kindCase = mMessage.getKindCase();
            out.writeInt(kindCase.getNumber());
            if (kindCase == KindCase.BOOL_VALUE) {
                out.writeBoolean(mMessage.getBoolValue());
            } else if (kindCase == KindCase.NUMBER_VALUE) {
                out.writeDouble(mMessage.getNumberValue());
            }
        }

        @Override
        protected @NonNull Value readMessage(@NonNull Parcel in) {
            final Value.Builder builder = Value.newBuilder();
            final KindCase kindCase = KindCase.forNumber(in.readInt());
            if (kindCase == KindCase.BOOL_VALUE) {
                builder.setBoolValue(in.readBoolean());
            } else if (kindCase == KindCase.NUMBER_VALUE) {
                builder.setNumberValue(in.readDouble());
            }
            return builder.build();
        }
    }

    @Before
    public void setUp() {
        mParcel = Parcel.obtain();
    }

    @After
    public void tearDown() {
        mParcel.recycle();
    }

    private static void assertEndPosition(@NonNull Parcel parcel) {
        assertEquals(parcel.dataPosition(), parcel.dataSize());
    }

    @Test
    public void testConstructorParcel() {
        PARCELABLE_VALUE.writeToParcel(mParcel, 0);
        mParcel.setDataPosition(0);
        assertEquals(VALUE, new ParcelableValue(mParcel).getWrapped());
        assertEndPosition(mParcel);
    }

    @Test
    public void testConstructorParcelOutdated() {
        new ParcelableValueEx(VALUE2).writeToParcel(mParcel, 0);
        mParcel.setDataPosition(0);
        assertEquals(KindCase.KIND_NOT_SET, new ParcelableValue(mParcel).getWrapped().getKindCase());
        assertEndPosition(mParcel);
    }

    @Test
    public void testConstructorParcelSequence() {
        new ParcelableValueEx(VALUE).writeToParcel(mParcel, 0);
        new ParcelableValueEx(VALUE2).writeToParcel(mParcel, 0);
        mParcel.setDataPosition(0);
        assertEquals(VALUE, new ParcelableValueEx(mParcel).getWrapped());
        assertEquals(VALUE2, new ParcelableValueEx(mParcel).getWrapped());
        assertEndPosition(mParcel);
    }

    @Test
    public void testConstructorParcelWrongSize() {
        mParcel.writeInt(16); // Wrong size, should be 12
        mParcel.writeInt(VALUE.getKindCase().getNumber());
        mParcel.writeBoolean(VALUE.getBoolValue());
        mParcel.setDataPosition(0);
        assertEquals(VALUE, new ParcelableValue(mParcel).getWrapped());
        assertEndPosition(mParcel);
    }

    @Test
    public void testConstructorParcelNegativeReadSize() {
        mParcel.writeBoolean(true);
        final int start = mParcel.dataPosition();
        PARCELABLE_VALUE.writeToParcel(mParcel, 0);
        mParcel.setDataPosition(start);
        final ParcelableValue wrapper = new ParcelableValue(mParcel) {
            @Override
            protected @NonNull Value readMessage(@NonNull Parcel in) {
                in.setDataPosition(0); // Never should do that
                return Value.getDefaultInstance();
            }
        };
        assertEquals(KindCase.KIND_NOT_SET, wrapper.getWrapped().getKindCase());
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testConstructorMessage() {
        assertEquals(VALUE, new ParcelableValue(VALUE).getWrapped());
    }

    @Test
    public void testWriteToParcel() {
        PARCELABLE_VALUE.writeToParcel(mParcel, 0);
        final int size = mParcel.dataSize();
        assertEquals(12, size);
        mParcel.setDataPosition(0);
        assertEquals(size, mParcel.readInt());
        assertEquals(KindCase.BOOL_VALUE.getNumber(), mParcel.readInt());
        assertEquals(VALUE.getBoolValue(), mParcel.readBoolean());
        assertEndPosition(mParcel);
    }

    @Test
    public void testWriteToParcelOther() {
        new ParcelableValueEx(VALUE2).writeToParcel(mParcel, 0);
        final int size = mParcel.dataSize();
        assertEquals(16, size);
        mParcel.setDataPosition(0);
        assertEquals(size, mParcel.readInt());
        assertEquals(KindCase.NUMBER_VALUE.getNumber(), mParcel.readInt());
        assertEquals(VALUE2.getNumberValue(), mParcel.readDouble(), 0.0);
        assertEndPosition(mParcel);
    }

    @Test
    public void testGetWrapped() {
        assertEquals(VALUE, PARCELABLE_VALUE.getWrapped());
    }

    @Test
    public void testDescribeContents() {
        assertEquals(0, PARCELABLE_VALUE.describeContents());
    }

    @Test
    public void testHashCode() {
        final ParcelableValue value1 = PARCELABLE_VALUE;
        final ParcelableValue value2 = new ParcelableValue(VALUE);
        final ParcelableValue value3 = new ParcelableValue(Value.newBuilder().setNumberValue(1).build());
        assertEquals(value1.hashCode(), value2.hashCode());
        assertNotEquals(value1.hashCode(), value3.hashCode());
    }

    @Test
    @SuppressWarnings({"AssertBetweenInconvertibleTypes", "EqualsWithItself"})
    public void testEquals() {
        final ParcelableValue value1 = PARCELABLE_VALUE;
        final ParcelableValue value2 = new ParcelableValue(VALUE);
        final ParcelableValue value3 = new ParcelableValue(Value.newBuilder().setNumberValue(1).build());
        assertEquals(value1, value1);
        assertEquals(value1, value2);
        assertNotEquals(value1, value3);
        assertNotEquals(value1, Boolean.TRUE);
        assertNotEquals(value1, null);
    }

    @Test
    public void testWriteByteString() {
        ParcelableMessage.writeByteString(mParcel, STRING);
        mParcel.setDataPosition(0);
        final int size = mParcel.readInt();
        assertEquals(STRING.size(), size);
        final byte[] array = new byte[size];
        mParcel.readByteArray(array);
        assertEquals(STRING, ByteString.copyFrom(array));
        assertEndPosition(mParcel);
    }

    @Test
    public void testWriteByteStringNull() {
        ParcelableMessage.writeByteString(mParcel, null);
        mParcel.setDataPosition(0);
        final int size = mParcel.readInt();
        assertEquals(VALUE_NOT_SET, size);
        assertEndPosition(mParcel);
    }

    @Test
    public void testReadByteString() {
        ParcelableMessage.writeByteString(mParcel, STRING);
        mParcel.setDataPosition(0);
        assertEquals(STRING, ParcelableMessage.readByteString(mParcel, null));
        assertEndPosition(mParcel);
    }

    @Test
    public void testReadByteStringNull() {
        ParcelableMessage.writeByteString(mParcel, null);
        mParcel.setDataPosition(0);
        assertNull(ParcelableMessage.readByteString(mParcel, null));
        assertEndPosition(mParcel);
    }

    @Test
    public void testReadByteStringDefault() {
        ParcelableMessage.writeByteString(mParcel, null);
        mParcel.setDataPosition(0);
        final ByteString defaultString = ByteString.empty();
        assertEquals(defaultString, ParcelableMessage.readByteString(mParcel, defaultString));
        assertEndPosition(mParcel);
    }
}
