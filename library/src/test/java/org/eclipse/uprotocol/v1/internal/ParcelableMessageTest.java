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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.os.BadParcelableException;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.protobuf.BoolValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.InvalidProtocolBufferException;

import org.eclipse.uprotocol.TestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ParcelableMessageTest extends TestBase {
    private static final BoolValue VALUE = BoolValue.newBuilder().setValue(true).build();
    private static final Int32Value VALUE2 = Int32Value.newBuilder().setValue(1).build();
    private static final ParcelableBoolValue PARCELABLE_VALUE = new ParcelableBoolValue(VALUE);
    private Parcel mParcel;

    @SuppressWarnings("NewClassNamingConvention")
    private static class ParcelableBoolValue extends ParcelableMessage<BoolValue> {

        public static final Creator<ParcelableBoolValue> CREATOR = new Creator<>() {
            public ParcelableBoolValue createFromParcel(Parcel in) {
                return new ParcelableBoolValue(in);
            }

            public ParcelableBoolValue[] newArray(int size) {
                return new ParcelableBoolValue[size];
            }
        };

        private ParcelableBoolValue(@NonNull Parcel in) {
            super(in);
        }

        public ParcelableBoolValue(@NonNull BoolValue value) {
            super(value);
        }

        @Override
        protected @NonNull BoolValue parse(@NonNull byte[] data) throws InvalidProtocolBufferException {
            return BoolValue.parseFrom(data);
        }
    }

    @SuppressWarnings("NewClassNamingConvention")
    private static class ParcelableInt32Value extends ParcelableMessage<Int32Value> {

        public static final Creator<ParcelableInt32Value> CREATOR = new Creator<>() {
            public ParcelableInt32Value createFromParcel(Parcel in) {
                return new ParcelableInt32Value(in);
            }

            public ParcelableInt32Value[] newArray(int size) {
                return new ParcelableInt32Value[size];
            }
        };

        private ParcelableInt32Value(@NonNull Parcel in) {
            super(in);
        }

        public ParcelableInt32Value(@NonNull Int32Value value) {
            super(value);
        }

        @Override
        protected @NonNull Int32Value parse(@NonNull byte[] data) throws InvalidProtocolBufferException {
            return Int32Value.parseFrom(data);
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
        assertEquals(VALUE, new ParcelableBoolValue(mParcel).getWrapped());
        assertEndPosition(mParcel);
    }

    @Test
    public void testConstructorParcelSequence() {
        new ParcelableBoolValue(VALUE).writeToParcel(mParcel, 0);
        new ParcelableInt32Value(VALUE2).writeToParcel(mParcel, 0);
        mParcel.setDataPosition(0);
        assertEquals(VALUE, new ParcelableBoolValue(mParcel).getWrapped());
        assertEquals(VALUE2, new ParcelableInt32Value(mParcel).getWrapped());
        assertEndPosition(mParcel);
    }

    @Test
    public void testConstructorParcelWrongSize() {
        mParcel.writeInt(-1); // Wrong size
        mParcel.writeByteArray(VALUE.toByteArray());
        mParcel.setDataPosition(0);
        assertThrows(BadParcelableException.class, () ->  new ParcelableBoolValue(mParcel));
    }

    @Test
    public void testConstructorParcelWrongData() {
        mParcel.writeInt(3);
        mParcel.writeByteArray(new byte[] { 1, 2, 3 });
        mParcel.setDataPosition(0);
        assertThrows(BadParcelableException.class, () ->  new ParcelableBoolValue(mParcel));
    }

    @Test
    public void testConstructorMessage() {
        assertEquals(VALUE, new ParcelableBoolValue(VALUE).getWrapped());
    }

    @Test
    public void testWriteToParcel() {
        final byte[] data = VALUE.toByteArray();
        final int size = data.length;
        PARCELABLE_VALUE.writeToParcel(mParcel, 0);
        mParcel.setDataPosition(0);
        assertEquals(size, mParcel.readInt());
        final byte[] actualData = new byte[size];
        mParcel.readByteArray(actualData);
        assertArrayEquals(data, actualData);
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
        final ParcelableBoolValue value1 = PARCELABLE_VALUE;
        final ParcelableBoolValue value2 = new ParcelableBoolValue(VALUE);
        final ParcelableBoolValue value3 = new ParcelableBoolValue(BoolValue.newBuilder().setValue(false).build());
        assertEquals(value1.hashCode(), value2.hashCode());
        assertNotEquals(value1.hashCode(), value3.hashCode());
    }

    @Test
    @SuppressWarnings({"AssertBetweenInconvertibleTypes", "EqualsWithItself"})
    public void testEquals() {
        final ParcelableBoolValue value1 = PARCELABLE_VALUE;
        final ParcelableBoolValue value2 = new ParcelableBoolValue(VALUE);
        final ParcelableBoolValue value3 = new ParcelableBoolValue(BoolValue.newBuilder().setValue(false).build());
        assertEquals(value1, value1);
        assertEquals(value1, value2);
        assertNotEquals(value1, value3);
        assertNotEquals(value1, Boolean.TRUE);
        assertNotEquals(value1, null);
    }
}
