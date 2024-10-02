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

import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.transport.builder.UMessageBuilder.request;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.TestBase;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SuppressWarnings("java:S2699")
public class PerformanceTest extends TestBase {
    private static final UUri URI = RESOURCE_URI;
    private static final UStatus STATUS = buildStatus(UCode.UNKNOWN, "Unknown error");
    private static final UMessage MESSAGE = request(CLIENT_URI, METHOD_URI, TTL).withToken(TOKEN).build(PAYLOAD);
    private static final String PROTOBUF = "Protobuf";
    private static final List<Integer> COUNTS = List.of(1000, 100, 10, 5, 1);

    private float writeParcelable(@NonNull Parcel parcel, @NonNull ParcelableMessage<?> parcelable, int count) {
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            parcel.setDataPosition(0);
            parcelable.writeToParcel(parcel, 0);
        }
        long end = System.nanoTime();
        return (float) (end - start) / (float) count;
    }

    private float readParcelable(@NonNull Parcel parcel, @NonNull Parcelable.Creator<?> creator, int count) {
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            parcel.setDataPosition(0);
            creator.createFromParcel(parcel);
        }
        long end = System.nanoTime();
        return (float) (end - start) / (float) count;
    }

    private void runPerformanceTestParcelableUUri(int count) {
        final ParcelableUUri parcelable = new ParcelableUUri(URI);
        final Parcel parcel = Parcel.obtain();
        float writeAverage = writeParcelable(parcel, parcelable, count);
        float readAverage = readParcelable(parcel, ParcelableUUri.CREATOR, count);
        parcel.recycle();
        printTableRow(count, writeAverage, readAverage, PROTOBUF);
    }

    private void runPerformanceTestParcelableUStatus(int count) {
        final ParcelableUStatus parcelable = new ParcelableUStatus(STATUS);
        final Parcel parcel = Parcel.obtain();
        float writeAverage = writeParcelable(parcel, parcelable, count);
        float readAverage = readParcelable(parcel, ParcelableUStatus.CREATOR, count);
        parcel.recycle();
        printTableRow(count, writeAverage, readAverage, PROTOBUF);
    }

    private void runPerformanceTestParcelableUMessage(int count) {
        final ParcelableUMessage parcelable = new ParcelableUMessage(MESSAGE);
        final Parcel parcel = Parcel.obtain();
        float writeAverage = writeParcelable(parcel, parcelable, count);
        float readAverage = readParcelable(parcel, ParcelableUMessage.CREATOR, count);
        parcel.recycle();
        printTableRow(count, writeAverage, readAverage, PROTOBUF);
    }

    private static void printTableHeader(@NonNull String title) {
        System.out.println(title + ":");
        System.out.println("   Loops  Write(ns)   Read(ns)     Method");
        System.out.println("-----------------------------------------");
    }

    @SuppressWarnings("SameParameterValue")
    private static void printTableRow(int count, float writeAverage, float readAverage, @NonNull String method) {
        System.out.printf("%8d %10.0f %10.0f   %s%n", count, writeAverage, readAverage, method);
    }

    private void runPerformanceTestUMessage(int count) {
        runPerformanceTestParcelableUMessage(count);
    }

    @Test
    public void testPerformanceUUri() {
        printTableHeader("UUri");
        COUNTS.forEach(this::runPerformanceTestParcelableUUri);
    }

    @Test
    public void testPerformanceUStatus() {
        printTableHeader("UStatus");
        COUNTS.forEach(this::runPerformanceTestParcelableUStatus);
    }

    @Test
    public void testPerformanceUMessage() {
        printTableHeader("UMessage");
        COUNTS.forEach(this::runPerformanceTestUMessage);
    }
}
