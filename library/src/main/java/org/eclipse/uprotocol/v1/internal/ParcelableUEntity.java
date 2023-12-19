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

import static com.google.common.base.Strings.nullToEmpty;

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.v1.UEntity;

/**
 * A parcelable wrapper for UEntity.
 */
public final class ParcelableUEntity extends ParcelableMessage<UEntity> {

    public static final Creator<ParcelableUEntity> CREATOR = new Creator<>() {
        public ParcelableUEntity createFromParcel(Parcel in) {
            return new ParcelableUEntity(in);
        }

        public ParcelableUEntity[] newArray(int size) {
            return new ParcelableUEntity[size];
        }
    };

    private ParcelableUEntity(@NonNull Parcel in) {
        super(in);
    }

    public ParcelableUEntity(@NonNull UEntity entity) {
        super(entity);
    }

    @Override
    protected void writeMessage(@NonNull Parcel out, int flags) {
        out.writeString(mMessage.getName());
        out.writeInt(mMessage.hasId() ? mMessage.getId() : VALUE_NOT_SET);
        out.writeInt(mMessage.hasVersionMajor() ? mMessage.getVersionMajor() : VALUE_NOT_SET);
        out.writeInt(mMessage.hasVersionMinor() ? mMessage.getVersionMinor() : VALUE_NOT_SET);
    }

    @Override
    protected @NonNull UEntity readMessage(@NonNull Parcel in) {
        final UEntity.Builder builder = UEntity.newBuilder();
        builder.setName(nullToEmpty(in.readString()));
        final int id = in.readInt();
        if (id >= 0) {
            builder.setId(id);
        }
        final int versionMajor = in.readInt();
        if (versionMajor >= 0) {
            builder.setVersionMajor(versionMajor);
        }
        final int versionMinor = in.readInt();
        if (versionMinor >= 0) {
            builder.setVersionMinor(versionMinor);
        }
        return builder.build();
    }
}
