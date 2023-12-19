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

import org.eclipse.uprotocol.v1.UStatus;

/**
 * A parcelable wrapper for UStatus.
 */
public final class ParcelableUStatus extends ParcelableMessage<UStatus> {

    public static final Creator<ParcelableUStatus> CREATOR = new Creator<>() {
        public ParcelableUStatus createFromParcel(Parcel in) {
            return new ParcelableUStatus(in);
        }

        public ParcelableUStatus[] newArray(int size) {
            return new ParcelableUStatus[size];
        }
    };

    private ParcelableUStatus(@NonNull Parcel in) {
        super(in);
    }

    public ParcelableUStatus(@NonNull UStatus status) {
        super(status);
    }

    @Override
    protected void writeMessage(@NonNull Parcel out, int flags) {
        out.writeInt(mMessage.getCodeValue());
        out.writeString(mMessage.hasMessage() ? mMessage.getMessage() : null);
    }

    @Override
    protected @NonNull UStatus readMessage(@NonNull Parcel in) {
        final UStatus.Builder builder = UStatus.newBuilder();
        builder.setCodeValue(in.readInt());
        final String message = in.readString();
        if (!isNullOrEmpty(message)) {
            builder.setMessage(message);
        }
        return builder.build();
    }
}
