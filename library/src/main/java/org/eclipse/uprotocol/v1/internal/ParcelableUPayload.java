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

import com.google.protobuf.ByteString;

import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UPayload.DataCase;

/**
 * A parcelable wrapper for UPayload.
 */
public final class ParcelableUPayload extends ParcelableMessage<UPayload> {

    public static final Creator<ParcelableUPayload> CREATOR = new Creator<>() {
        public ParcelableUPayload createFromParcel(Parcel in) {
                return new ParcelableUPayload(in);
        }

        public ParcelableUPayload[] newArray(int size) {
            return new ParcelableUPayload[size];
        }
    };

    private ParcelableUPayload(@NonNull Parcel in) {
        super(in);
    }

    public ParcelableUPayload(@NonNull UPayload payload) {
        super(payload);
    }

    @Override
    protected void writeMessage(@NonNull Parcel out, int flags) {
        out.writeInt(mMessage.getFormatValue());
        final DataCase dataCase = mMessage.getDataCase();
        out.writeInt(dataCase.getNumber());
        switch (dataCase) {
            case REFERENCE -> {
                out.writeInt(mMessage.getLength());
                out.writeLong(mMessage.getReference());
            }
            case VALUE -> writeByteString(out, mMessage.getValue());
            default -> {
                // Nothing to do
            }
        }
    }

    @Override
    protected @NonNull UPayload readMessage(@NonNull Parcel in) {
        final UPayload.Builder builder = UPayload.newBuilder();
        builder.setFormatValue(in.readInt());
        final DataCase dataCase = DataCase.forNumber(in.readInt());
        switch (dataCase) {
            case REFERENCE -> {
                builder.setLength(in.readInt());
                builder.setReference(in.readLong());
            }
            case VALUE -> builder.setValue(readByteString(in, ByteString.empty()));
            default -> {
                // Nothing to do
            }
        }
        return builder.build();
    }
}
