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
import static com.google.common.base.Strings.nullToEmpty;

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.v1.UResource;

/**
 * A parcelable wrapper for UResource.
 */
public final class ParcelableUResource extends ParcelableMessage<UResource> {

    public static final Creator<ParcelableUResource> CREATOR = new Creator<>() {
        public ParcelableUResource createFromParcel(Parcel in) {
            return new ParcelableUResource(in);
        }

        public ParcelableUResource[] newArray(int size) {
            return new ParcelableUResource[size];
        }
    };

    private ParcelableUResource(@NonNull Parcel in) {
        super(in);
    }

    public ParcelableUResource(@NonNull UResource resource) {
        super(resource);
    }

    @Override
    protected void writeMessage(@NonNull Parcel out, int flags) {
        out.writeString(mMessage.getName());
        out.writeString(mMessage.hasInstance() ? mMessage.getInstance() : null);
        out.writeString(mMessage.hasMessage() ? mMessage.getMessage() : null);
        out.writeInt(mMessage.hasId() ? mMessage.getId() : VALUE_NOT_SET);
    }

    @Override
    protected  @NonNull UResource readMessage(@NonNull Parcel in) {
        final UResource.Builder builder = UResource.newBuilder();
        builder.setName(nullToEmpty(in.readString()));
        final String instance = in.readString();
        if (!isNullOrEmpty(instance)) {
            builder.setInstance(instance);
        }
        final String message = in.readString();
        if (!isNullOrEmpty(message)) {
            builder.setMessage(message);
        }
        final int id = in.readInt();
        if (id >= 0) {
            builder.setId(id);
        }
        return builder.build();
    }
}
