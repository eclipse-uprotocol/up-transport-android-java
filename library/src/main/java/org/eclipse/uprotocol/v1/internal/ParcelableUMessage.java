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

import org.eclipse.uprotocol.v1.UMessage;

/**
 * A parcelable wrapper for UMessage.
 */
public final class ParcelableUMessage extends ParcelableMessage<UMessage> {

    public static final Creator<ParcelableUMessage> CREATOR = new Creator<>() {
        public ParcelableUMessage createFromParcel(Parcel in) {
            return new ParcelableUMessage(in);
        }

        public ParcelableUMessage[] newArray(int size) {
            return new ParcelableUMessage[size];
        }
    };

    private ParcelableUMessage(@NonNull Parcel in) {
        super(in);
    }

    public ParcelableUMessage(@NonNull UMessage message) {
        super(message);
    }

    @Override
    protected void writeMessage(@NonNull Parcel out, int flags) {
        out.writeParcelable(mMessage.hasSource() ? new ParcelableUUri(mMessage.getSource()) : null, flags);
        out.writeParcelable(mMessage.hasAttributes() ? new ParcelableUAttributes(mMessage.getAttributes()) : null, flags);
        out.writeParcelable(mMessage.hasPayload() ? new ParcelableUPayload(mMessage.getPayload()) : null, flags);
    }

    @Override
    protected @NonNull UMessage readMessage(@NonNull Parcel in) {
        final UMessage.Builder builder = UMessage.newBuilder();
        final ParcelableUUri source = in.readParcelable(ParcelableUUri.class.getClassLoader());
        if (source != null) {
            builder.setSource(source.getWrapped());
        }
        final ParcelableUAttributes attributes = in.readParcelable(ParcelableUAttributes.class.getClassLoader());
        if (attributes != null) {
            builder.setAttributes(attributes.getWrapped());
        }
        final ParcelableUPayload payload = in.readParcelable(ParcelableUPayload.class.getClassLoader());
        if (payload != null) {
            builder.setPayload(payload.getWrapped());
        }
        return builder.build();
    }
}
