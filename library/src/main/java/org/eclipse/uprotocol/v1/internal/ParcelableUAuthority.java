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

import static com.google.common.base.Strings.isNullOrEmpty;

import android.os.Parcel;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import org.eclipse.uprotocol.v1.UAuthority;

/**
 * A parcelable wrapper for UAuthority.
 */
public final class ParcelableUAuthority extends ParcelableMessage<UAuthority> {

    public static final Creator<ParcelableUAuthority> CREATOR = new Creator<>() {
        public ParcelableUAuthority createFromParcel(Parcel in) {
            return new ParcelableUAuthority(in);
        }

        public ParcelableUAuthority[] newArray(int size) {
            return new ParcelableUAuthority[size];
        }
    };

    private ParcelableUAuthority(@NonNull Parcel in) {
        super(in);
    }

    public ParcelableUAuthority(@NonNull UAuthority authority) {
        super(authority);
    }

    @Override
    protected void writeMessage(@NonNull Parcel out, int flags) {
        out.writeString(mMessage.hasName() ? mMessage.getName() : null);
        writeByteString(out, mMessage.hasIp() ? mMessage.getIp() : null);
        writeByteString(out, mMessage.hasId() ? mMessage.getId() : null);
    }

    @Override
    protected @NonNull UAuthority readMessage(@NonNull Parcel in) {
        final UAuthority.Builder builder = UAuthority.newBuilder();
        final String name = in.readString();
        if (!isNullOrEmpty(name)) {
            builder.setName(name);
        }
        final ByteString ip = readByteString(in, null);
        if (ip != null) {
            builder.setIp(ip);
        }
        final ByteString id = readByteString(in, null);
        if (id != null) {
            builder.setId(id);
        }
        return builder.build();
    }
}
