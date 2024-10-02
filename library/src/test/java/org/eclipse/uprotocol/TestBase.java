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
import static org.eclipse.uprotocol.communication.UPayload.packToAny;
import static org.eclipse.uprotocol.transport.UTransportAndroid.META_DATA_ENTITY_ID;
import static org.eclipse.uprotocol.transport.UTransportAndroid.META_DATA_ENTITY_VERSION;
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

import org.eclipse.uprotocol.communication.CallOptions;
import org.eclipse.uprotocol.communication.UPayload;
import org.eclipse.uprotocol.communication.UStatusException;
import org.eclipse.uprotocol.uuid.factory.UuidFactory;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUID;
import org.eclipse.uprotocol.v1.UUri;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("SameParameterValue")
public class TestBase {
    protected static final String AUTHORITY_REMOTE = "cloud";
    protected static final int VERSION = 1;
    protected static final int CLIENT_ID = 0x50;
    protected static final int SERVICE_ID = 0x51;
    protected static final int METHOD_ID = 0x1;
    protected static final int RESOURCE_ID = 0x8000;
    protected static final int RESOURCE2_ID = 0x8001;
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
    protected static final UUri RESOURCE2_URI = UUri.newBuilder(SERVICE_URI)
            .setResourceId(RESOURCE2_ID)
            .build();
    protected static final UUri RESOURCE_URI_REMOTE = UUri.newBuilder(RESOURCE_URI)
            .setAuthorityName(AUTHORITY_REMOTE)
            .build();

    protected static final UUID ID = createId();
    protected static final int TTL = CallOptions.DEFAULT.timeout();
    protected static final String TOKEN =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG" +
            "4gU21pdGgiLCJpYXQiOjE1MTYyMzkwMjJ9.Q_w2AVguPRU2KskCXwR7ZHl09TQXEntfEA8Jj2_Jyew";
    protected static final UPayload PAYLOAD = packToAny(Empty.getDefaultInstance());
    protected static final long DELAY_MS = 100;

    protected static @NonNull UUID createId() {
        return UuidFactory.Factories.UPROTOCOL.factory().create();
    }

    protected static @NonNull Bundle buildEntityMetadata(UUri uri) {
        return buildEntityMetadata(uri.getUeId(), uri.getUeVersionMajor());
    }

    protected static @NonNull Bundle buildEntityMetadata(int id, int version) {
        final Bundle bundle = new Bundle();
        if (id > 0) {
            bundle.putInt(META_DATA_ENTITY_ID, id);
        }
        if (version > 0) {
            bundle.putInt(META_DATA_ENTITY_VERSION, version);
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

    protected void sleep(long timeout) {
        try {
            new CompletableFuture<>().get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {}
    }
}
