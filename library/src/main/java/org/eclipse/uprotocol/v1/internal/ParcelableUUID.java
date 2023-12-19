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

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.v1.UUID;

/**
 * A parcelable wrapper for UUID.
 */
public final class ParcelableUUID extends ParcelableMessage<UUID> {

    public static final Creator<ParcelableUUID> CREATOR = new Creator<>() {
        public ParcelableUUID createFromParcel(Parcel in) {
            return new ParcelableUUID(in);
        }

        public ParcelableUUID[] newArray(int size) {
            return new ParcelableUUID[size];
        }
    };

    private ParcelableUUID(@NonNull Parcel in) {
        super(in);
    }

    public ParcelableUUID(@NonNull UUID uuid) {
        super(uuid);
    }

    @Override
    protected void writeMessage(@NonNull Parcel out, int flags) {
        out.writeLong(mMessage.getMsb());
        out.writeLong(mMessage.getLsb());
    }

    @Override
    protected @NonNull UUID readMessage(@NonNull Parcel in) {
        final UUID.Builder builder = UUID.newBuilder();
        builder.setMsb(in.readLong());
        builder.setLsb(in.readLong());
        return builder.build();
    }
}
