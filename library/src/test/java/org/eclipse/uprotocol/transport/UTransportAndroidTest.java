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
package org.eclipse.uprotocol.transport;

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.transport.BuildConfig.LIBRARY_PACKAGE_NAME;
import static org.eclipse.uprotocol.transport.BuildConfig.VERSION_NAME;
import static org.eclipse.uprotocol.transport.builder.UMessageBuilder.notification;
import static org.eclipse.uprotocol.transport.builder.UMessageBuilder.publish;
import static org.eclipse.uprotocol.uri.factory.UriFactory.WILDCARD_ENTITY_ID;
import static org.eclipse.uprotocol.uri.factory.UriFactory.WILDCARD_ENTITY_VERSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.TestBase;
import org.eclipse.uprotocol.core.ubus.UBusManager;
import org.eclipse.uprotocol.uri.validator.UriFilter;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class UTransportAndroidTest extends TestBase {
    private static final UMessage MESSAGE = notification(RESOURCE_URI, CLIENT_URI).build(PAYLOAD);
    private static final UMessage MESSAGE2 = publish(RESOURCE_URI).build(PAYLOAD);
    private static final UriFilter FILTER = new UriFilter(RESOURCE_URI, CLIENT_URI);
    private static final UriFilter FILTER2 = new UriFilter(RESOURCE2_URI, CLIENT_URI);

    private Context mContext;
    private String mPackageName;
    private ShadowPackageManager mShadowPackageManager;
    private Handler mHandler;
    private Executor mExecutor;
    private UListener mListener;
    private UListener mListener2;
    private UBusManager mManager;
    private UTransportAndroid mTransport;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mPackageName = mContext.getPackageName();
        mShadowPackageManager = Shadows.shadowOf(mContext.getPackageManager());
        mHandler = newMockHandler();
        mExecutor = newMockExecutor();
        mListener = mock(UListener.class);
        mListener2 = mock(UListener.class);
        mManager = mock(UBusManager.class);
        doCallRealMethod().when(mManager).disableDispatchingQuietly(any());
        injectPackage(buildPackageInfo(mPackageName, buildEntityMetadata(CLIENT_URI)));
        mTransport = new UTransportAndroid(mContext, CLIENT_URI, mManager, mExecutor);
        mTransport.setLoggable(Log.INFO);
    }

    private void injectPackage(@NonNull PackageInfo packageInfo) {
        mShadowPackageManager.installPackage(packageInfo);
    }

    @Test
    public void testConstants() {
        assertEquals("uprotocol.permission.ACCESS_UBUS", UTransportAndroid.PERMISSION_ACCESS_UBUS);
        assertEquals("uprotocol.entity.id", UTransportAndroid.META_DATA_ENTITY_ID);
        assertEquals("uprotocol.entity.version", UTransportAndroid.META_DATA_ENTITY_VERSION);
    }

    @Test
    public void testCreate() {
        injectPackage(buildPackageInfo(mPackageName,
                buildServiceInfo(new ComponentName(mPackageName, ".Service"), buildEntityMetadata(SERVICE_URI))));
        assertNotNull(UTransportAndroid.create(mContext, SERVICE_URI, mExecutor));
    }

    @Test
    public void testCreateWithoutSource() {
        assertNotNull(UTransportAndroid.create(mContext, mExecutor));
    }

    @Test
    public void testCreateWitHandler() {
        assertNotNull(UTransportAndroid.create(mContext, mHandler));
    }

    @Test
    public void testCreateWithDefaultCallbackThread() {
        assertNotNull(UTransportAndroid.create(mContext, (Handler) null));
        assertNotNull(UTransportAndroid.create(mContext, (Executor) null));
    }

    @Test
    public void testCreateWithBadContextWrapper() {
        final ContextWrapper context = spy(new ContextWrapper(mContext));
        doReturn(null).when(context).getBaseContext();
        assertThrows(NullPointerException.class, () -> UTransportAndroid.create(context, mExecutor));
    }

    @Test
    public void testCreatePackageManagerNotAvailable() {
        assertThrows(NullPointerException.class, () -> UTransportAndroid.create(mock(Context.class), mExecutor));
    }

    @Test
    public void testCreatePackageNotFound() throws NameNotFoundException {
        final PackageManager manager = mock(PackageManager.class);
        doThrow(new NameNotFoundException()).when(manager).getPackageInfo(anyString(), anyInt());
        final Context context = spy(new ContextWrapper(mContext));
        doReturn(manager).when(context).getPackageManager();
        assertThrows(SecurityException.class, () -> UTransportAndroid.create(context, mExecutor));
    }

    @Test
    public void testCreateEntityDifferentDeclared() {
        injectPackage(buildPackageInfo(mPackageName, buildEntityMetadata(SERVICE_ID, VERSION)));
        assertThrows(SecurityException.class, () -> UTransportAndroid.create(mContext, CLIENT_URI, mExecutor));
    }

    @Test
    public void testCreateEntityNotDeclared() {
        injectPackage(buildPackageInfo(mPackageName));
        assertThrows(SecurityException.class, () -> UTransportAndroid.create(mContext, mExecutor));
    }

    @Test
    public void testCreateEntityIdNotDeclared() {
        injectPackage(buildPackageInfo(mPackageName, buildEntityMetadata(0, VERSION)));
        assertThrows(SecurityException.class, () -> UTransportAndroid.create(mContext, mExecutor));
    }

    @Test
    public void testCreateEntityIdInvalid() {
        injectPackage(buildPackageInfo(mPackageName, buildEntityMetadata(WILDCARD_ENTITY_ID, VERSION)));
        assertThrows(SecurityException.class, () -> UTransportAndroid.create(mContext, mExecutor));
    }

    @Test
    public void testCreateEntityVersionNotDeclared() {
        injectPackage(buildPackageInfo(mPackageName, buildEntityMetadata(CLIENT_ID, 0)));
        assertThrows(SecurityException.class, () -> UTransportAndroid.create(mContext, mExecutor));
    }

    @Test
    public void testCreateEntityVersionInvalid() {
        injectPackage(buildPackageInfo(mPackageName, buildEntityMetadata(CLIENT_ID, WILDCARD_ENTITY_VERSION)));
        assertThrows(SecurityException.class, () -> UTransportAndroid.create(mContext, mExecutor));
    }

    @Test
    public void testCreateVerboseVersionLogged() {
        final String tag = mTransport.getTag();
        ShadowLog.setLoggable(tag, Log.VERBOSE);
        assertNotNull(UTransportAndroid.create(mContext, mTransport.getSource(), mHandler));
        ShadowLog.getLogsForTag(tag).stream()
                .filter(it -> it.msg.contains(LIBRARY_PACKAGE_NAME) && it.msg.contains(VERSION_NAME))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Version is not printed"));
    }

    @Test
    public void testOpen() {
        final CompletableFuture<UStatus> future = new CompletableFuture<>();
        doReturn(future).when(mManager).connect();
        assertEquals(future, mTransport.open());
    }

    @Test
    public void testClose() {
        doReturn(CompletableFuture.completedFuture(STATUS_OK)).when(mManager).disconnect();
        mTransport.close();
        verify(mManager, times(1)).disconnect();
    }

    @Test
    public void testIsOpened() {
        assertFalse(mTransport.isOpened());
        doReturn(true).when(mManager).isConnected();
        assertTrue(mTransport.isOpened());
    }

    @Test
    public void testOnConnected() {
        testOnConnectionInterrupted();
        mTransport.getConnectionCallback().onConnected();
        verify(mManager, times(2)).enableDispatching(any());
    }

    @Test
    public void testOnDisconnected() {
        testRegisterListener();
        mTransport.getConnectionCallback().onDisconnected();
        verify(mManager, never()).disableDispatching(any());
        mTransport.getConnectionCallback().onConnected();
        verify(mManager, times(1)).enableDispatching(any());
    }

    @Test
    public void testOnConnectionInterrupted() {
        testRegisterListener();
        mTransport.getConnectionCallback().onConnected();
        mTransport.getConnectionCallback().onConnectionInterrupted();
        verify(mManager, never()).disableDispatching(any());
    }

    @Test
    public void testGetSource() {
        assertEquals(CLIENT_URI, mTransport.getSource());
    }

    @Test
    public void testSend() {
        doReturn(STATUS_OK).when(mManager).send(MESSAGE);
        assertStatus(UCode.OK, getOrThrow(mTransport.send(MESSAGE)));
    }

    @Test
    public void testRegisterListener() {
        doReturn(STATUS_OK).when(mManager).enableDispatching(FILTER);
        assertStatus(UCode.OK, getOrThrow(mTransport.registerListener(FILTER.source(), FILTER.sink(), mListener)));
        verify(mManager, times(1)).enableDispatching(FILTER);
    }

    @Test
    public void testRegisterListenerWithNull() {
        assertStatus(UCode.INVALID_ARGUMENT, getOrThrow(mTransport.registerListener(FILTER.source(), null)));
        verify(mManager, never()).enableDispatching(any());
    }

    @Test
    public void testRegisterListenerSame() {
        testRegisterListener();
        assertStatus(UCode.OK, getOrThrow(mTransport.registerListener(FILTER.source(), FILTER.sink(), mListener)));
        verify(mManager, times(1)).enableDispatching(FILTER);
    }

    @Test
    public void testRegisterListenerNotFirst() {
        testRegisterListener();
        assertStatus(UCode.OK, getOrThrow(mTransport.registerListener(FILTER.source(), FILTER.sink(), mListener2)));
        verify(mManager, times(1)).enableDispatching(FILTER);
    }

    @Test
    public void testRegisterListenerDifferentFilter() {
        doReturn(STATUS_OK).when(mManager).enableDispatching(FILTER);
        doReturn(STATUS_OK).when(mManager).enableDispatching(FILTER2);
        assertStatus(UCode.OK, getOrThrow(mTransport.registerListener(FILTER.source(), FILTER.sink(), mListener)));
        assertStatus(UCode.OK, getOrThrow(mTransport.registerListener(FILTER2.source(), FILTER2.sink(), mListener)));
        verify(mManager, times(1)).enableDispatching(FILTER);
        verify(mManager, times(1)).enableDispatching(FILTER2);
    }

    @Test
    public void testRegisterListenerFailed() {
        doReturn(buildStatus(UCode.UNAUTHENTICATED)).when(mManager).enableDispatching(FILTER);
        assertStatus(UCode.UNAUTHENTICATED, getOrThrow(mTransport.registerListener(FILTER.source(), FILTER.sink(), mListener)));
    }

    @Test
    public void testRegisterListenerWhenReconnected() {
        testRegisterListener();
        mTransport.getConnectionCallback().onConnectionInterrupted();
        verify(mManager, timeout(DELAY_MS).times(0)).disableDispatching(FILTER);
        mTransport.getConnectionCallback().onConnected();
        verify(mManager, timeout(DELAY_MS).times(2)).enableDispatching(FILTER);
    }

    @Test
    public void testUnregisterListener() {
        testRegisterListener();
        doReturn(STATUS_OK).when(mManager).disableDispatching(FILTER);
        assertStatus(UCode.OK, getOrThrow(mTransport.unregisterListener(FILTER.source(), FILTER.sink(), mListener)));
        verify(mManager, times(1)).disableDispatching(FILTER);
    }

    @Test
    public void testUnregisterListenerWithNullListener() {
        assertStatus(UCode.INVALID_ARGUMENT, getOrThrow(mTransport.unregisterListener(FILTER.source(), null)));
        verify(mManager, never()).disableDispatching(FILTER);
    }

    @Test
    public void testUnregisterListenerSame() {
        testUnregisterListener();
        assertStatus(UCode.OK, getOrThrow(mTransport.unregisterListener(FILTER.source(), FILTER.sink(), mListener)));
        verify(mManager, times(1)).disableDispatching(FILTER);
    }

    @Test
    public void testUnregisterListenerNotRegistered() {
        testRegisterListener();
        assertStatus(UCode.OK, getOrThrow(mTransport.unregisterListener(FILTER.source(), FILTER.sink(), mListener2)));
        verify(mManager, times(0)).disableDispatching(FILTER);
    }

    @Test
    public void testUnregisterListenerNotLast() {
        testRegisterListenerNotFirst();
        assertStatus(UCode.OK, getOrThrow(mTransport.unregisterListener(FILTER.source(), FILTER.sink(), mListener)));
        verify(mManager, never()).disableDispatching(FILTER);
    }

    @Test
    public void testUnregisterListenerLast() {
        testUnregisterListenerNotLast();
        assertStatus(UCode.OK, getOrThrow(mTransport.unregisterListener(FILTER.source(), FILTER.sink(), mListener2)));
        verify(mManager, times(1)).disableDispatching(FILTER);
    }

    @Test
    public void testUnregisterListenerWhenDisconnected() {
        testRegisterListener();
        mTransport.getConnectionCallback().onDisconnected();
        mTransport.getListener().onReceive(MESSAGE);
        verify(mListener, timeout(DELAY_MS).times(0)).onReceive(MESSAGE);
    }

    @Test
    public void testOnReceiveMessage() {
        testRegisterListenerNotFirst();
        mTransport.getListener().onReceive(MESSAGE);
        verify(mListener, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
        verify(mListener2, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
    }

    @Test
    public void testOnReceiveMessageNotRegistered() {
        testUnregisterListener();
        mTransport.getListener().onReceive(MESSAGE);
        verify(mListener, timeout(DELAY_MS).times(0)).onReceive(MESSAGE);
    }

    @Test
    public void testOnReceiveMessageListenerFailure() {
        mTransport.setLoggable(Log.VERBOSE);
        doThrow(new RuntimeException()).when(mListener).onReceive(any());
        testRegisterListenerNotFirst();
        mTransport.getListener().onReceive(MESSAGE);
        verify(mListener, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
        verify(mListener2, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
    }

    @Test
    public void testOnReceiveMessageNotMatching() {
        testRegisterListener();
        mTransport.getListener().onReceive(MESSAGE2);
        verify(mListener, timeout(DELAY_MS).times(0)).onReceive(MESSAGE2);
    }
}
