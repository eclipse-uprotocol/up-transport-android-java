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

import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.google.protobuf.Empty;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.transport.builder.UAttributesBuilder;
import org.eclipse.uprotocol.uri.builder.UResourceBuilder;
import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UPriority;
import org.eclipse.uprotocol.v1.UResource;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("SameParameterValue")
public class TestBase {
    protected static final UEntity SERVICE = UEntity.newBuilder()
            .setName("client.test")
            .setVersionMajor(1)
            .build();
    protected static final UEntity CLIENT = UEntity.newBuilder()
            .setName("client.test")
            .setVersionMajor(1)
            .build();
    protected static final UResource RESOURCE = UResource.newBuilder()
            .setName("resource")
            .setInstance("main")
            .setMessage("State")
            .build();
    protected static final UResource RESOURCE2 = UResource.newBuilder()
            .setName("resource2")
            .setInstance("main2")
            .setMessage("State2")
            .build();
    protected static final UUri RESOURCE_URI = UUri.newBuilder()
            .setEntity(SERVICE)
            .setResource(RESOURCE)
            .build();
    protected static final UUri RESOURCE2_URI = UUri.newBuilder()
            .setEntity(SERVICE)
            .setResource(RESOURCE2)
            .build();
    protected static final UUri METHOD_URI = UUri.newBuilder()
            .setEntity(SERVICE)
            .setResource(UResourceBuilder.forRpcRequest("method"))
            .build();
    protected static final UUri METHOD2_URI = UUri.newBuilder()
            .setEntity(SERVICE)
            .setResource(UResourceBuilder.forRpcRequest("method2"))
            .build();
    protected static final UUri RESPONSE_URI = UUri.newBuilder()
            .setEntity(CLIENT)
            .setResource(UResourceBuilder.forRpcResponse())
            .build();
    protected static final UUri CLIENT_URI = UUri.newBuilder()
            .setEntity(CLIENT)
            .build();
    protected static final UPayload PAYLOAD = packToAny(Empty.getDefaultInstance());
    protected static final int TTL = 5000;
    protected static final long CONNECTION_TIMEOUT_MS = 3000;
    protected static final long DELAY_MS = 100;

    protected static @NonNull UAttributes buildPublishAttributes() {
        return newPublishAttributesBuilder().build();
    }


    protected static @NonNull UAttributesBuilder newPublishAttributesBuilder() {
        return UAttributesBuilder.publish(UPriority.UPRIORITY_CS0);
    }

    protected static @NonNull UAttributesBuilder newNotificationAttributesBuilder(@NonNull UUri sink) {
        return UAttributesBuilder.notification(UPriority.UPRIORITY_CS0, sink);
    }

    protected static @NonNull UMessage buildMessage(UUri source, UPayload payload, UAttributes attributes) {
        final UMessage.Builder builder = UMessage.newBuilder();
        if (source != null) {
            builder.setSource(source);
        }
        if (payload != null) {
            builder.setPayload(payload);
        }
        if (attributes != null) {
            builder.setAttributes(attributes);
        }
        return builder.build();
    }

    protected static void connect(@NonNull UPClient client) {
        assertStatus(UCode.OK, getOrThrow(client.connect().toCompletableFuture(), CONNECTION_TIMEOUT_MS));
        assertTrue(client.isConnected());
    }

    protected static void disconnect(@NonNull UPClient client) {
        assertStatus(UCode.OK, getOrThrow(client.disconnect().toCompletableFuture()));
        assertTrue(client.isDisconnected());
    }

    protected static void assertStatus(@NonNull UCode code, @NonNull UStatus status) {
        assertEquals(code, status.getCode());
    }

    protected static <T> T getOrThrow(@NonNull Future<T> future) {
        return getOrThrow(future, DELAY_MS);
    }

    protected static <T> T getOrThrow(@NonNull Future<T> future, long timeout) {
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new UStatusException(toStatus(e));
        }
    }
}
