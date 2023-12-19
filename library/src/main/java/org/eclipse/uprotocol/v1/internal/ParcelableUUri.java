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

import org.eclipse.uprotocol.v1.UUri;

/**
 * A parcelable wrapper for UUri.
 */
public final class ParcelableUUri extends ParcelableMessage<UUri> {

    public static final Creator<ParcelableUUri> CREATOR = new Creator<>() {
        public ParcelableUUri createFromParcel(Parcel in) {
            return new ParcelableUUri(in);
        }

        public ParcelableUUri[] newArray(int size) {
            return new ParcelableUUri[size];
        }
    };

    private ParcelableUUri(@NonNull Parcel in) {
        super(in);
    }

    public ParcelableUUri(@NonNull UUri uri) {
        super(uri);
    }

    @Override
    protected void writeMessage(@NonNull Parcel out, int flags) {
        out.writeParcelable(mMessage.hasAuthority() ? new ParcelableUAuthority(mMessage.getAuthority()) : null, flags);
        out.writeParcelable(mMessage.hasEntity() ? new ParcelableUEntity(mMessage.getEntity()) : null, flags);
        out.writeParcelable(mMessage.hasResource() ? new ParcelableUResource(mMessage.getResource()) : null, flags);
    }

    @Override
    protected @NonNull UUri readMessage(@NonNull Parcel in) {
        final UUri.Builder builder = UUri.newBuilder();
        final ParcelableUAuthority authority = in.readParcelable(ParcelableUAuthority.class.getClassLoader());
        if (authority != null) {
            builder.setAuthority(authority.getWrapped());
        }
        final ParcelableUEntity entity = in.readParcelable(ParcelableUEntity.class.getClassLoader());
        if (entity != null) {
            builder.setEntity(entity.getWrapped());
        }
        final ParcelableUResource resource = in.readParcelable(ParcelableUResource.class.getClassLoader());
        if (resource != null) {
            builder.setResource(resource.getWrapped());
        }
        return builder.build();
    }
}
