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

import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UMessageType;
import org.eclipse.uprotocol.v1.UPriority;

/**
 * A parcelable wrapper for UAttributes.
 */
public final class ParcelableUAttributes extends ParcelableMessage<UAttributes> {
    private static final int END_OF_FIELDS = 0;

    public static final Creator<ParcelableUAttributes> CREATOR = new Creator<>() {
        public ParcelableUAttributes createFromParcel(Parcel in) {
            return new ParcelableUAttributes(in);
        }

        public ParcelableUAttributes[] newArray(int size) {
            return new ParcelableUAttributes[size];
        }
    };

    private ParcelableUAttributes(@NonNull Parcel in) {
        super(in);
    }

    public ParcelableUAttributes(@NonNull UAttributes attributes) {
        super(attributes);
    }

    @Override
    protected void writeMessage(@NonNull Parcel out, int flags) {
        if (mMessage.hasId()) {
            out.writeInt(UAttributes.ID_FIELD_NUMBER);
            out.writeParcelable(new ParcelableUUID(mMessage.getId()), flags);
        }
        if (mMessage.getTypeValue() > UMessageType.UMESSAGE_TYPE_UNSPECIFIED_VALUE) {
            out.writeInt(UAttributes.TYPE_FIELD_NUMBER);
            out.writeInt(mMessage.getTypeValue());
        }
        if (mMessage.hasSink()) {
            out.writeInt(UAttributes.SINK_FIELD_NUMBER);
            out.writeParcelable(new ParcelableUUri(mMessage.getSink()), flags);
        }
        if (mMessage.getPriorityValue() > UPriority.UPRIORITY_UNSPECIFIED_VALUE) {
            out.writeInt(UAttributes.PRIORITY_FIELD_NUMBER);
            out.writeInt(mMessage.getPriorityValue());
        }
        if (mMessage.hasTtl()) {
            out.writeInt(UAttributes.TTL_FIELD_NUMBER);
            out.writeInt(mMessage.getTtl());
        }
        if (mMessage.hasPermissionLevel()) {
            out.writeInt(UAttributes.PERMISSION_LEVEL_FIELD_NUMBER);
            out.writeInt(mMessage.getPermissionLevel());
        }
        if (mMessage.hasCommstatus()) {
            out.writeInt(UAttributes.COMMSTATUS_FIELD_NUMBER);
            out.writeInt(mMessage.getCommstatus());
        }
        if (mMessage.hasReqid()) {
            out.writeInt(UAttributes.REQID_FIELD_NUMBER);
            out.writeParcelable(new ParcelableUUID(mMessage.getReqid()), 0);
        }
        if (mMessage.hasToken()) {
            out.writeInt(UAttributes.TOKEN_FIELD_NUMBER);
            out.writeString(mMessage.getToken());
        }
        out.writeInt(END_OF_FIELDS);
    }

    @Override
    protected @NonNull UAttributes readMessage(@NonNull Parcel in) {
        final UAttributes.Builder builder = UAttributes.newBuilder();
        boolean fieldRead;
        do {
            fieldRead = readField(in, builder);
        } while (fieldRead);
        return builder.build();
    }

    private static boolean readField(@NonNull Parcel in, @NonNull UAttributes.Builder builder) {
        final int fieldNumber = in.readInt();
        switch (fieldNumber) {
            case UAttributes.ID_FIELD_NUMBER -> {
                final ParcelableUUID id = in.readParcelable(ParcelableUUID.class.getClassLoader());
                if (id != null) {
                    builder.setId(id.getWrapped());
                }
            }
            case UAttributes.TYPE_FIELD_NUMBER -> builder.setTypeValue(in.readInt());
            case UAttributes.SINK_FIELD_NUMBER -> {
                final ParcelableUUri sink = in.readParcelable(ParcelableUUri.class.getClassLoader());
                if (sink != null) {
                    builder.setSink(sink.getWrapped());
                }
            }
            case UAttributes.PRIORITY_FIELD_NUMBER -> builder.setPriorityValue(in.readInt());
            case UAttributes.TTL_FIELD_NUMBER -> builder.setTtl(in.readInt());
            case UAttributes.PERMISSION_LEVEL_FIELD_NUMBER -> builder.setPermissionLevel(in.readInt());
            case UAttributes.COMMSTATUS_FIELD_NUMBER -> builder.setCommstatus(in.readInt());
            case UAttributes.REQID_FIELD_NUMBER -> {
                final ParcelableUUID reqId = in.readParcelable(ParcelableUUID.class.getClassLoader());
                if (reqId != null) {
                    builder.setReqid(reqId.getWrapped());
                }
            }
            case UAttributes.TOKEN_FIELD_NUMBER -> {
                final String token = in.readString();
                if (!isNullOrEmpty(token)) {
                    builder.setToken(token);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
