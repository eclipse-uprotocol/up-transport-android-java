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
package org.eclipse.uprotocol;

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.communication.UPayload.packToAny;
import static org.junit.Assert.assertEquals;

import androidx.annotation.NonNull;

import com.google.protobuf.Empty;

import org.eclipse.uprotocol.communication.CallOptions;
import org.eclipse.uprotocol.communication.UPayload;
import org.eclipse.uprotocol.communication.UStatusException;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("SameParameterValue")
public class TestBase {
    protected static final int VERSION = 1;
    protected static final int CLIENT_ID = 0x50;
    protected static final int SERVICE_ID = 0x50;
    protected static final int METHOD_ID = 0x1;
    protected static final int RESOURCE_ID = 0x8000;
    protected static final UUri CLIENT_URI = UUri.newBuilder()
            .setUeId(CLIENT_ID)
            .setUeVersionMajor(VERSION)
            .build();
    protected static final UUri SERVICE_URI = UUri.newBuilder()
            .setUeId(SERVICE_ID)
            .setUeVersionMajor(VERSION)
            .build();
    protected static final UUri METHOD_URI = UUri.newBuilder(SERVICE_URI)
            .setResourceId(METHOD_ID)
            .build();
    protected static final UUri RESOURCE_URI = UUri.newBuilder(SERVICE_URI)
            .setResourceId(RESOURCE_ID)
            .build();
    protected static final UPayload PAYLOAD = packToAny(Empty.getDefaultInstance());
    protected static final UPayload PAYLOAD2 = packToAny(STATUS_OK);
    protected static final int TTL = CallOptions.DEFAULT.timeout();
    protected static final long CONNECTION_TIMEOUT_MS = 3000;
    protected static final long DELAY_MS = 100;

    protected static void assertStatus(@NonNull UCode code, @NonNull UStatus status) {
        assertEquals(code, status.getCode());
    }

    protected static <T> T getOrThrow(@NonNull CompletionStage<T> stage) {
        return getOrThrow(stage, DELAY_MS);
    }

    protected static <T> T getOrThrow(@NonNull CompletionStage<T> stage, long timeout) {
        try {
            return stage.toCompletableFuture().get(timeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new UStatusException(toStatus(e));
        }
    }
}
