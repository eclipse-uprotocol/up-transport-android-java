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

import static org.eclipse.uprotocol.UPClient.META_DATA_ENTITY_ID;
import static org.eclipse.uprotocol.UPClient.META_DATA_ENTITY_NAME;
import static org.eclipse.uprotocol.UPClient.META_DATA_ENTITY_VERSION;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.protobuf.Empty;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.rpc.CallOptions;
import org.eclipse.uprotocol.transport.builder.UAttributesBuilder;
import org.eclipse.uprotocol.uri.factory.UResourceBuilder;
import org.eclipse.uprotocol.uuid.factory.UuidFactory;
import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UMessageType;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UPriority;
import org.eclipse.uprotocol.v1.UResource;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUID;
import org.eclipse.uprotocol.v1.UUri;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("SameParameterValue")
public class TestBase {
    protected static final UAuthority AUTHORITY_REMOTE = UAuthority.newBuilder()
            .setName("cloud")
            .build();
    protected static final UEntity SERVICE = UEntity.newBuilder()
            .setName("test.service")
            .setVersionMajor(1)
            .build();
    protected static final UEntity CLIENT = UEntity.newBuilder()
            .setName("test.client")
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
    protected static final UUri RESOURCE_URI_REMOTE =  UUri.newBuilder()
            .setAuthority(AUTHORITY_REMOTE)
            .setEntity(SERVICE)
            .setResource(RESOURCE)
            .build();
    protected static final UUri CLIENT_URI = UUri.newBuilder()
            .setEntity(CLIENT)
            .build();
    protected static final UUri SERVICE_URI = UUri.newBuilder()
            .setEntity(SERVICE)
            .build();
    protected static final UUID ID = createId();
    protected static final UUID ID2 = createId();
    protected static final int TTL = 1000;
    protected static final String TOKEN =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG" +
            "4gU21pdGgiLCJpYXQiOjE1MTYyMzkwMjJ9.Q_w2AVguPRU2KskCXwR7ZHl09TQXEntfEA8Jj2_Jyew";
    protected static final CallOptions OPTIONS = CallOptions.newBuilder()
            .withTimeout(TTL)
            .withToken(TOKEN)
            .build();
    protected static final UAttributes ATTRIBUTES = UAttributes.newBuilder()
            .setId(ID)
            .setType(UMessageType.UMESSAGE_TYPE_RESPONSE)
            .setSource(METHOD_URI)
            .setSink(RESPONSE_URI)
            .setPriority(UPriority.UPRIORITY_CS4)
            .setTtl(TTL)
            .setPermissionLevel(5)
            .setCommstatus(UCode.DEADLINE_EXCEEDED_VALUE)
            .setReqid(ID2)
            .setToken(TOKEN)
            .build();
    protected static final UPayload PAYLOAD = packToAny(Empty.getDefaultInstance());
    protected static final long DELAY_MS = 100;

    protected static @NonNull UUID createId() {
        return UuidFactory.Factories.UPROTOCOL.factory().create();
    }

    protected static @NonNull UAttributes buildPublishAttributes(@NonNull UUri source) {
        return newPublishAttributesBuilder(source).build();
    }

    protected static @NonNull UAttributes buildRequestAttributes(@NonNull UUri responseUri, @NonNull UUri methodUri) {
        return newRequestAttributesBuilder(responseUri, methodUri).build();
    }

    protected static @NonNull UAttributes buildResponseAttributes(
            @NonNull UUri methodUri, @NonNull UUri responseUri, @NonNull UUID requestId) {
        return newResponseAttributesBuilder(methodUri, responseUri, requestId).build();
    }

    protected static @NonNull UAttributesBuilder newPublishAttributesBuilder(@NonNull UUri source) {
        return UAttributesBuilder.publish(source, UPriority.UPRIORITY_CS0);
    }

    protected static @NonNull UAttributesBuilder newNotificationAttributesBuilder(
            @NonNull UUri source, @NonNull UUri sink) {
        return UAttributesBuilder.notification(source, sink, UPriority.UPRIORITY_CS0);
    }

    protected static @NonNull UAttributesBuilder newRequestAttributesBuilder(
            @NonNull UUri responseUri, @NonNull UUri methodUri) {
        return UAttributesBuilder.request(responseUri, methodUri, UPriority.UPRIORITY_CS4, TTL);
    }

    protected static @NonNull UAttributesBuilder newResponseAttributesBuilder(
            @NonNull UUri methodUri, @NonNull UUri responseUri, @NonNull UUID requestId) {
        return UAttributesBuilder.response(methodUri, responseUri, UPriority.UPRIORITY_CS4, requestId);
    }

    protected static @NonNull UMessage buildMessage(UPayload payload, UAttributes attributes) {
        final UMessage.Builder builder = UMessage.newBuilder();
        if (payload != null) {
            builder.setPayload(payload);
        }
        if (attributes != null) {
            builder.setAttributes(attributes);
        }
        return builder.build();
    }

    protected static @NonNull Bundle buildMetadata(@NonNull UEntity entity) {
        final Bundle bundle = new Bundle();
        bundle.putString(META_DATA_ENTITY_NAME, entity.getName());
        if (entity.hasVersionMajor()) {
            bundle.putInt(META_DATA_ENTITY_VERSION, entity.getVersionMajor());
        }
        if (entity.hasId()) {
            bundle.putInt(META_DATA_ENTITY_ID, entity.getId());
        }
        return bundle;
    }

    protected static @NonNull PackageInfo buildPackageInfo(@NonNull String packageName, Bundle metaData) {
        final ApplicationInfo appInfo = buildApplicationInfo(packageName, metaData);
        final PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = appInfo;
        packageInfo.packageName = packageName;
        return packageInfo;
    }

    protected static PackageInfo buildPackageInfo(@NonNull String packageName, ServiceInfo... services) {
        final ApplicationInfo appInfo = buildApplicationInfo(packageName, null);
        final PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = appInfo;
        packageInfo.packageName = packageName;
        packageInfo.services = services;
        for (ServiceInfo service : services) {
            service.applicationInfo = appInfo;
        }
        return packageInfo;
    }

    protected static ApplicationInfo buildApplicationInfo(@NonNull String packageName, Bundle metaData) {
        final ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.flags = ApplicationInfo.FLAG_INSTALLED;
        appInfo.packageName = packageName;
        if (metaData != null && !metaData.isEmpty()) {
            appInfo.metaData = metaData;
        }
        return appInfo;
    }

    protected static ServiceInfo buildServiceInfo(ComponentName component, Bundle metaData) {
        final ServiceInfo serviceInfo = new ServiceInfo();
        if (component != null) {
            serviceInfo.packageName = component.getPackageName();
            serviceInfo.name = component.getClassName();
        }
        if (metaData != null && !metaData.isEmpty()) {
            serviceInfo.metaData = metaData;
        }
        return serviceInfo;
    }

    protected static @NonNull Handler newMockHandler() {
        final Handler handler = mock(Handler.class);
        doReturn(Looper.myLooper()).when(handler).getLooper();
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return true;
        }).when(handler).post(any());
        doAnswer(invocation -> {
            final android.os.Message msg = invocation.getArgument(0, android.os.Message.class);
            msg.getCallback().run();
            return true;
        }).when(handler).sendMessageAtTime(any(), anyLong());
        return handler;
    }

    protected static @NonNull Executor newMockExecutor() {
        final Executor executor = mock(Executor.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(executor).execute(any());
        return executor;
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

    protected void sleep(long timeout) {
        try {
            new CompletableFuture<>().get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {}
    }
}
