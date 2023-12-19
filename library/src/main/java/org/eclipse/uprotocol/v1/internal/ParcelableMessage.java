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
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;

import java.util.Objects;

/**
 * A parcelable wrapper base for protobuf messages.
 */
public abstract class ParcelableMessage<T extends Message> implements Parcelable {
    protected static final int VALUE_NOT_SET = -1;

    protected final T mMessage;

    protected ParcelableMessage(@NonNull Parcel in) {
        mMessage = readFromParcel(in);
    }

    protected ParcelableMessage(@NonNull T message) {
        mMessage = message;
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        final int start = out.dataPosition();
        out.writeInt(0); // Placeholder for size
        writeMessage(out, flags);
        final int end = out.dataPosition();
        final int size = end - start;
        out.setDataPosition(start);
        out.writeInt(size);
        out.setDataPosition(end);
    }

    protected abstract void writeMessage(@NonNull Parcel out, int flags);

    private @NonNull T readFromParcel(@NonNull Parcel in) {
        final int start = in.dataPosition();
        final int size = in.readInt();
        final T message = readMessage(in);
        int end = in.dataPosition();
        skipUnknownFields(in, start, end, size);
        return message;
    }

    protected abstract @NonNull T readMessage(@NonNull Parcel in);

    private static void skipUnknownFields(@NonNull Parcel parcel, int start, int end, int totalSize) {
        final int size = end - start;
        if (size >= 0 && size < totalSize) {
            end = start + totalSize;
            if (end <= parcel.dataSize()) {
                parcel.setDataPosition(end);
            }
        }
    }

    protected static void writeByteString(@NonNull Parcel out, ByteString string) {
        if (string == null) {
            out.writeInt(VALUE_NOT_SET);
        } else {
            final byte[] array = string.toByteArray();
            out.writeInt(array.length);
            out.writeByteArray(array);
        }
    }

    protected static ByteString readByteString(@NonNull Parcel in, ByteString defaultString) {
        final int length = in.readInt();
        if (length < 0) {
            return defaultString;
        } else {
            final byte[] array = new byte[length];
            in.readByteArray(array);
            return ByteString.copyFrom(array);
        }
    }

    public @NonNull T getWrapped() {
        return mMessage;
    }

    public int describeContents() {
        return 0;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mMessage);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ParcelableMessage<?> other)) {
            return false;
        }
        return Objects.equals(mMessage, other.mMessage);
    }
}
