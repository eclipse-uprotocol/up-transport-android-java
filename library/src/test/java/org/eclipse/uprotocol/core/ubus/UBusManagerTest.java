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
package org.eclipse.uprotocol.core.ubus;

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.core.ubus.UBusManager.ACTION_BIND_UBUS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.TestBase;
import org.eclipse.uprotocol.client.R;
import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.internal.ParcelableUMessage;
import org.eclipse.uprotocol.v1.internal.ParcelableUStatus;
import org.eclipse.uprotocol.v1.internal.ParcelableUUri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class UBusManagerTest extends TestBase {
    private static final String SERVICE_PACKAGE = "org.eclipse.uprotocol.core.ubus";
    private static final ComponentName SERVICE = new ComponentName(SERVICE_PACKAGE, SERVICE_PACKAGE + ".UBusService");
    private static final UMessage MESSAGE = buildMessage(RESOURCE_URI, PAYLOAD, buildPublishAttributes());
    private static final long REBIND_DELAY_MS = 1000 + DELAY_MS;

    private Context mContext;
    private ConnectionCallback mConnectionCallback;
    private UListener mListener;
    private UBusManager mManager;
    private ServiceConnection mServiceConnection;
    private IBinder mServiceBinder;
    private IUBus mService;

    @Before
    public void setUp() throws RemoteException {
        mContext = mock(Context.class);
        mConnectionCallback = mock(ConnectionCallback.class);
        mListener = mock(UListener.class);
        setServiceConfig(SERVICE_PACKAGE);
        mManager = new UBusManager(mContext, CLIENT, mConnectionCallback, mListener);
        mManager.setLoggable(Log.INFO);
        prepareService();
    }

    private void setServiceConfig(@NonNull String config) {
        doReturn(config).when(mContext).getString(R.string.config_UBusService);
    }

    private void prepareService() throws RemoteException {
        mService = mock(IUBus.class);
        mServiceBinder = mock(IBinder.class);
        doReturn(mService).when(mServiceBinder).queryLocalInterface(anyString());
        doReturn(mServiceBinder).when(mService).asBinder();
        doReturn(new ParcelableUStatus(STATUS_OK)).when(mService).registerClient(any(), any(), any(), anyInt(), any());
        doReturn(new ParcelableUStatus(STATUS_OK)).when(mService).unregisterClient(any());
        doReturn(new ParcelableUStatus(STATUS_OK)).when(mService).enableDispatching(any(), any(), any());
        doReturn(new ParcelableUStatus(STATUS_OK)).when(mService).disableDispatching(any(), any(), any());
        doReturn(new ParcelableUStatus(STATUS_OK)).when(mService).send(any(), any());
        doReturn(new ParcelableUMessage[] { new ParcelableUMessage(MESSAGE) })
                .when(mService).pull(any(), anyInt(), any(), any());
        prepareService(true, connection -> {
            mServiceConnection = connection;
            mServiceConnection.onServiceConnected(SERVICE, mServiceBinder);
        });
    }

    private void prepareService(boolean available, @NonNull Consumer<ServiceConnection> onBindCallback) {
        doAnswer(invocation -> {
            onBindCallback.accept(invocation.getArgument(1));
            return available;
        }).when(mContext).bindService(any(), any(), anyInt());
    }

    private void assertDisconnected() {
        verify(mConnectionCallback, timeout(DELAY_MS).times(1)).onDisconnected();
        assertTrue(mManager.isDisconnected());
    }

    private void assertDisconnected(@NonNull CompletableFuture<UStatus> disconnectionFuture) {
        assertStatus(UCode.OK, getOrThrow(disconnectionFuture));
        assertDisconnected();
    }

    private void assertConnecting() {
        verify(mContext, timeout(DELAY_MS).atLeast(1)).bindService(any(), any(), anyInt());
        assertTrue(mManager.isConnecting());
    }

    private void assertConnected() {
        verify(mConnectionCallback, timeout(DELAY_MS).times(1)).onConnected();
        assertTrue(mManager.isConnected());
    }

    private void assertConnected(@NonNull CompletableFuture<UStatus> connectionFuture) {
        assertStatus(UCode.OK, getOrThrow(connectionFuture));
        assertConnected();
    }

    private void assertConnectionFailed(@NonNull CompletableFuture<UStatus> connectionFuture, @NonNull UCode code) {
        assertStatus(code, getOrThrow(connectionFuture));
        verify(mConnectionCallback, timeout(DELAY_MS).times(0)).onConnected();
        assertTrue(mManager.isDisconnected());
    }

    private void assertConnectionInterrupted() {
        verify(mConnectionCallback, timeout(DELAY_MS).times(1)).onConnectionInterrupted();
        assertTrue(mManager.isDisconnected());
    }

    @Test
    public void testConnect() {
        assertConnected(mManager.connect());
        verify(mContext, times(1)).bindService(argThat(intent -> {
            assertEquals(ACTION_BIND_UBUS, intent.getAction());
            assertEquals(SERVICE_PACKAGE, intent.getPackage());
            return true;
        }), any(), anyInt());
    }

    @Test
    public void testConnectWithConfiguredPackage() {
        final String packageName = "some.package";
        setServiceConfig(packageName);
        mManager = new UBusManager(mContext, CLIENT, mConnectionCallback, mListener);
        assertConnected(mManager.connect());
        verify(mContext, times(1)).bindService(argThat(intent -> {
            assertEquals(ACTION_BIND_UBUS, intent.getAction());
            assertEquals(packageName, intent.getPackage());
            return true;
        }), any(), anyInt());
    }

    @Test
    public void testConnectWithConfiguredComponent() {
        setServiceConfig(SERVICE.flattenToShortString());
        mManager = new UBusManager(mContext, CLIENT, mConnectionCallback, mListener);
        assertConnected(mManager.connect());
        verify(mContext, times(1)).bindService(argThat(intent -> {
            assertEquals(ACTION_BIND_UBUS, intent.getAction());
            assertEquals(SERVICE, intent.getComponent());
            return true;
        }), any(), anyInt());
    }

    @Test
    public void testConnectWithEmptyConfig() {
        setServiceConfig("");
        mManager = new UBusManager(mContext, CLIENT, mConnectionCallback, mListener);
        assertConnected(mManager.connect());
        verify(mContext, times(1)).bindService(argThat(intent -> {
            assertEquals(ACTION_BIND_UBUS, intent.getAction());
            assertNull(intent.getPackage());
            return true;
        }), any(), anyInt());
    }

    @Test
    public void testConnectNoService() {
        prepareService(false, connection -> {});
        assertConnectionFailed(mManager.connect(), UCode.NOT_FOUND);
    }

    @Test
    public void testConnectServiceDisabled() {
        prepareService(false, connection -> {
            mServiceConnection = connection;
            mServiceConnection.onNullBinding(SERVICE);
        });
        assertConnectionFailed(mManager.connect(), UCode.NOT_FOUND);
    }

    @Test
    public void testConnectPermissionDenied() {
        prepareService(true, connection -> { throw new SecurityException("Permission denied"); });
        assertConnectionFailed(mManager.connect(), UCode.PERMISSION_DENIED);
    }

    @Test
    public void testConnectUnauthenticated() throws RemoteException {
        doReturn(new ParcelableUStatus(buildStatus(UCode.UNAUTHENTICATED)))
                .when(mService).registerClient(any(), any(), any(), anyInt(), any());
        assertConnectionFailed(mManager.connect(), UCode.UNAUTHENTICATED);
    }

    @Test
    public void testConnectExceptionally() throws RemoteException {
        doThrow(new UStatusException(UCode.INTERNAL, "Failure"))
                .when(mService).registerClient(any(), any(), any(), anyInt(), any());
        assertConnectionFailed(mManager.connect(), UCode.INTERNAL);
    }

    @Test
    public void testConnectAlreadyConnected() {
        mManager.setLoggable(Log.DEBUG);
        testConnect();
        assertConnected(mManager.connect());
    }

    @Test
    public void testConnectAlreadyConnecting() {
        prepareService(true, connection -> mServiceConnection = connection);
        final CompletableFuture<UStatus> future1 = mManager.connect();
        final CompletableFuture<UStatus> future2 = mManager.connect();
        assertConnecting();
        mServiceConnection.onServiceConnected(SERVICE, mServiceBinder);
        assertConnected(future1);
        assertConnected(future2);
    }

    @Test
    public void testConnectOnServiceConnectedWithSameInstance() {
        assertConnected(mManager.connect());
        mServiceConnection.onServiceConnected(SERVICE, mServiceBinder);
        assertConnected();
    }

    @Test
    public void testConnectOnServiceConnectedWithSameInstanceDebug() {
        mManager.setLoggable(Log.DEBUG);
        testConnectOnServiceConnectedWithSameInstance();
    }

    @Test
    public void testConnectOnServiceConnectedWithOtherInstance() throws RemoteException {
        assertConnected(mManager.connect());
        sleep(DELAY_MS);
        prepareService();
        mServiceConnection.onServiceConnected(SERVICE, mServiceBinder);
        assertConnected();
    }

    @Test
    public void testDisconnect() {
        testConnect();
        assertDisconnected(mManager.disconnect());
    }

    @Test
    public void testDisconnectAlreadyDisconnected() {
        testDisconnect();
        assertDisconnected(mManager.disconnect());
    }

    @Test
    public void testDisconnectWhileConnecting() {
        prepareService(true, connection -> mServiceConnection = connection);
        final CompletableFuture<UStatus> future = mManager.connect();
        assertConnecting();
        assertDisconnected(mManager.disconnect());
        mServiceConnection.onServiceConnected(SERVICE, mServiceBinder);
        assertConnectionFailed(future, UCode.CANCELLED);
    }

    @Test
    public void testDisconnectWhileConnectingVerbose() {
        mManager.setLoggable(Log.VERBOSE);
        testDisconnectWhileConnecting();
    }

    @Test
    public void testDisconnectExceptionally() throws RemoteException {
        testConnect();
        doThrow(new UStatusException(UCode.INTERNAL, "Failure")).when(mService).unregisterClient(any());
        assertDisconnected(mManager.disconnect());
    }

    @Test
    public void testOnServiceDisconnected() {
        testConnect();
        mServiceConnection.onServiceDisconnected(SERVICE);
        assertConnectionInterrupted();
    }

    @Test
    public void testOnServiceDisconnectedAlreadyDisconnected() {
        testDisconnect();
        mServiceConnection.onServiceDisconnected(SERVICE);
        verify(mConnectionCallback, timeout(DELAY_MS).times(0)).onConnectionInterrupted();
    }

    @Test
    public void testOnServiceDisconnectedAlreadyDisconnectedDebug() {
        mManager.setLoggable(Log.DEBUG);
        testOnServiceDisconnectedAlreadyDisconnected();
    }

    @Test
    public void testOnBindingDied() {
        testOnServiceDisconnected();
        mServiceConnection.onBindingDied(SERVICE);
        verify(mContext, timeout(REBIND_DELAY_MS).times(1)).unbindService(any());
        verify(mContext, times(2)).bindService(any(), any(), anyInt());
    }

    @Test
    public void testOnBindingDiedNotConnected() {
        testDisconnect();
        mServiceConnection.onBindingDied(SERVICE);
        verify(mContext, timeout(REBIND_DELAY_MS).times(1)).unbindService(any());
        verify(mContext, times(1)).bindService(any(), any(), anyInt());
    }

    @Test
    public void testOnBindingDiedNotConnectedDebug() {
        mManager.setLoggable(Log.DEBUG);
        testOnBindingDiedNotConnected();
    }

    @Test
    public void testOnBindingDiedAlreadyReconnected() {
        testConnect();
        mServiceConnection.onBindingDied(SERVICE);
        verify(mContext, timeout(REBIND_DELAY_MS).times(0)).unbindService(any());
        verify(mContext, times(1)).bindService(any(), any(), anyInt());
        assertTrue(mManager.isConnected());
    }

    @Test
    public void testOnBindingDiedAlreadyReconnectedDebug() {
        mManager.setLoggable(Log.DEBUG);
        testOnBindingDiedAlreadyReconnected();
    }

    @Test
    public void testOnBindingDiedRebindFailed() {
        testOnServiceDisconnected();
        prepareService(false, connection -> {});
        mServiceConnection.onBindingDied(SERVICE);
        verify(mContext, timeout(REBIND_DELAY_MS).times(1)).unbindService(any());
        verify(mContext, times(2)).bindService(any(), any(), anyInt());
        assertTrue(mManager.isDisconnected());
    }

    @Test
    public void testConnectionStates() {
        prepareService(true, connection -> mServiceConnection = connection);
        mManager.connect();
        assertConnecting();
        assertFalse(mManager.isDisconnected());
        assertFalse(mManager.isConnected());
        mServiceConnection.onServiceConnected(SERVICE, mServiceBinder);
        assertConnected();
        assertFalse(mManager.isDisconnected());
        assertFalse(mManager.isConnecting());
        mManager.disconnect();
        assertDisconnected();
        assertFalse(mManager.isConnecting());
        assertFalse(mManager.isConnected());
    }

    @Test
    public void testCalculateRebindDelaySeconds() {
        List.of(1L, 2L, 4L, 8L, 16L, 32L, 32L, 32L)
                .forEach(expected -> assertEquals((long) expected, mManager.calculateRebindDelaySeconds()));
    }

    @Test
    public void testEnableDispatching() throws RemoteException {
        testConnect();
        assertStatus(UCode.OK, mManager.enableDispatching(RESOURCE_URI));
        verify(mService, times(1)).enableDispatching(eq(new ParcelableUUri(RESOURCE_URI)), any(), any());
    }

    @Test
    public void testEnableDispatchingDisconnected() throws RemoteException {
        assertStatus(UCode.UNAVAILABLE, mManager.enableDispatching(RESOURCE_URI));
        verify(mService, never()).enableDispatching(any(), any(), any());
    }

    @Test
    public void testDisableDispatching() throws RemoteException {
        testConnect();
        assertStatus(UCode.OK, mManager.disableDispatching(RESOURCE_URI));
        verify(mService, times(1)).disableDispatching(eq(new ParcelableUUri(RESOURCE_URI)), any(), any());
    }

    @Test
    public void testDisableDispatchingDisconnected() throws RemoteException {
        assertStatus(UCode.UNAVAILABLE, mManager.disableDispatching(RESOURCE_URI));
        verify(mService, never()).disableDispatching(any(), any(), any());
    }

    @Test
    public void testDisableDispatchingDisconnectedDebug() throws RemoteException {
        mManager.setLoggable(Log.DEBUG);
        assertStatus(UCode.UNAVAILABLE, mManager.disableDispatching(RESOURCE_URI));
        verify(mService, never()).disableDispatching(any(), any(), any());
    }

    @Test
    public void testDisableDispatchingQuietly() throws RemoteException {
        testConnect();
        mManager.disableDispatchingQuietly(RESOURCE_URI);
        verify(mService, times(1)).disableDispatching(eq(new ParcelableUUri(RESOURCE_URI)), any(), any());
    }

    @Test
    public void testGetLastMessage() throws RemoteException {
        testConnect();
        assertEquals(MESSAGE, mManager.getLastMessage(RESOURCE_URI));
        verify(mService, times(1)).pull(eq(new ParcelableUUri(RESOURCE_URI)), eq(1), any(), any());
    }

    @Test
    public void testGetLastMessageNotAvailable() throws RemoteException {
        testConnect();
        doReturn(new ParcelableUMessage[0]).when(mService).pull(any(), anyInt(), any(), any());
        assertNull(mManager.getLastMessage(RESOURCE_URI));
        doReturn(null).when(mService).pull(any(), anyInt(), any(), any());
        assertNull(mManager.getLastMessage(RESOURCE_URI));
        verify(mService, times(2)).pull(eq(new ParcelableUUri(RESOURCE_URI)), eq(1), any(), any());
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testGetLastMessageInvalidArgument() throws RemoteException {
        testConnect();
        doThrow(new NullPointerException()).when(mService).pull(any(), anyInt(), any(), any());
        assertNull(mManager.getLastMessage(null));
    }

    @Test
    public void testGetLastMessageDisconnected() throws RemoteException {
        assertNull(mManager.getLastMessage(RESOURCE_URI));
        verify(mService, never()).pull(any(), anyInt(), any(), any());
    }

    @Test
    public void testSend() throws RemoteException {
        testConnect();
        assertStatus(UCode.OK, mManager.send(MESSAGE));
        verify(mService, times(1)).send(eq(new ParcelableUMessage(MESSAGE)), any());
    }

    @Test
    public void testSendDisconnected() throws RemoteException {
        assertStatus(UCode.UNAVAILABLE, mManager.send(MESSAGE));
        verify(mService, never()).send(any(), any());
    }

    @Test
    public void testOnReceive() throws RemoteException {
        final ArgumentCaptor<IUListener> captor = ArgumentCaptor.forClass(IUListener.class);
        doReturn(new ParcelableUStatus(STATUS_OK)).when(mService)
                .registerClient(any(), any(), any(), anyInt(), captor.capture());
        testConnect();
        final IUListener serviceListener = captor.getValue();
        serviceListener.onReceive(new ParcelableUMessage(MESSAGE));
        verify(mListener, times(1)).onReceive(MESSAGE);
    }
}
