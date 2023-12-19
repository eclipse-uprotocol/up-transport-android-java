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

import static org.junit.Assert.assertEquals;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.TestBase;
import org.eclipse.uprotocol.v1.UAttributes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ParcelableUAttributesTest extends TestBase {
    private Parcel mParcel;

    @Before
    public void setUp() {
        mParcel = Parcel.obtain();
    }

    @After
    public void tearDown() {
        mParcel.recycle();
    }

    private void checkWriteAndRead(@NonNull UAttributes message) {
        final int start = mParcel.dataPosition();
        new ParcelableUAttributes(message).writeToParcel(mParcel, 0);
        mParcel.setDataPosition(start);
        assertEquals(message, ParcelableUAttributes.CREATOR.createFromParcel(mParcel).getWrapped());
    }

    @Test
    public void testConstructor() {
        assertEquals(ATTRIBUTES, new ParcelableUAttributes(ATTRIBUTES).getWrapped());
    }

    @Test
    public void testNewArray() {
        final ParcelableUAttributes[] array = ParcelableUAttributes.CREATOR.newArray(2);
        assertEquals(2, array.length);
    }

    @Test
    public void testCreateFromParcel() {
        checkWriteAndRead(ATTRIBUTES);
    }

    @Test
    public void testCreateFromParcelWithoutId() {
        checkWriteAndRead(UAttributes.newBuilder(ATTRIBUTES).clearId().build());
    }

    @Test
    public void testCreateFromParcelWithoutType() {
        checkWriteAndRead(UAttributes.newBuilder(ATTRIBUTES).clearType().build());
    }

    @Test
    public void testCreateFromParcelWithoutSink() {
        checkWriteAndRead(UAttributes.newBuilder(ATTRIBUTES).clearSink().build());
    }

    @Test
    public void testCreateFromParcelWithoutPriority() {
        checkWriteAndRead(UAttributes.newBuilder(ATTRIBUTES).clearPriority().build());
    }

    @Test
    public void testCreateFromParcelWithoutTtl() {
        checkWriteAndRead(UAttributes.newBuilder(ATTRIBUTES).clearTtl().build());
    }

    @Test
    public void testCreateFromParcelWithoutPermissionLevel() {
        checkWriteAndRead(UAttributes.newBuilder(ATTRIBUTES).clearPermissionLevel().build());
    }

    @Test
    public void testCreateFromParcelWithoutCommStatus() {
        checkWriteAndRead(UAttributes.newBuilder(ATTRIBUTES).clearCommstatus().build());
    }

    @Test
    public void testCreateFromParcelWithoutReqId() {
        checkWriteAndRead(UAttributes.newBuilder(ATTRIBUTES).clearReqid().build());
    }

    @Test
    public void testCreateFromParcelWithoutToken() {
        checkWriteAndRead(UAttributes.newBuilder(ATTRIBUTES).clearToken().build());
    }

    @Test
    public void testCreateFromParcelEmpty() {
        checkWriteAndRead(UAttributes.getDefaultInstance());
    }

    @Test
    public void testCreateFromParcelWithNullFields() {
        mParcel.writeInt(40);
        mParcel.writeInt(UAttributes.ID_FIELD_NUMBER);
        mParcel.writeParcelable(null, 0);
        mParcel.writeInt(UAttributes.SINK_FIELD_NUMBER);
        mParcel.writeParcelable(null, 0);
        mParcel.writeInt(UAttributes.REQID_FIELD_NUMBER);
        mParcel.writeParcelable(null, 0);
        mParcel.writeInt(UAttributes.TOKEN_FIELD_NUMBER);
        mParcel.writeString(null);
        mParcel.writeInt(0);
        mParcel.setDataPosition(0);
        assertEquals(UAttributes.getDefaultInstance(), ParcelableUAttributes.CREATOR.createFromParcel(mParcel).getWrapped());
    }
}
