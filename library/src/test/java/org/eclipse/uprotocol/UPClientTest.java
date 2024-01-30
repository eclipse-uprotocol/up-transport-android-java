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

import static org.eclipse.uprotocol.client.BuildConfig.LIBRARY_PACKAGE_NAME;
import static org.eclipse.uprotocol.client.BuildConfig.VERSION_NAME;
import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
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
import static org.mockito.Mockito.mockStatic;
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

import com.google.protobuf.Int32Value;

import org.eclipse.uprotocol.UPClient.ServiceLifecycleListener;
import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.core.ubus.UBusManager;
import org.eclipse.uprotocol.rpc.URpcListener;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.transport.builder.UAttributesBuilder;
import org.eclipse.uprotocol.transport.validate.UAttributesValidator;
import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UMessageType;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UPriority;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;
import org.eclipse.uprotocol.validation.ValidationResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class UPClientTest extends TestBase {
    private static final UMessage MESSAGE = buildMessage(RESOURCE_URI, PAYLOAD, buildPublishAttributes());
    private static final UPayload REQUEST_PAYLOAD = packToAny(Int32Value.newBuilder().setValue(1).build());
    private static final UPayload RESPONSE_PAYLOAD = packToAny(STATUS_OK);

    private Context mContext;
    private String mPackageName;
    private ShadowPackageManager mShadowPackageManager;
    private Handler mHandler;
    private Executor mExecutor;
    private ServiceLifecycleListener mServiceLifecycleListener;
    private UListener mListener;
    private UListener mListener2;
    private URpcListener mRequestListener;
    private URpcListener mRequestListener2;
    private UBusManager mManager;
    private UPClient mClient;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mPackageName = mContext.getPackageName();
        mShadowPackageManager = Shadows.shadowOf(mContext.getPackageManager());
        mHandler = newMockHandler();
        mExecutor = newMockExecutor();
        mServiceLifecycleListener = mock(ServiceLifecycleListener.class);
        mListener = mock(UListener.class);
        mListener2 = mock(UListener.class);
        mRequestListener = mock(URpcListener.class);
        mRequestListener2 = mock(URpcListener.class);
        mManager = mock(UBusManager.class);
        injectPackage(buildPackageInfo(mPackageName, buildMetadata(CLIENT)));
        mClient = new UPClient(mContext, CLIENT, mManager, mExecutor, mServiceLifecycleListener);
        mClient.setLoggable(Log.INFO);
    }

    private void injectPackage(@NonNull PackageInfo packageInfo) {
        mShadowPackageManager.installPackage(packageInfo);
    }

    private static void redirectMessages(@NonNull UBusManager manager, @NonNull UPClient client) {
        doAnswer(invocation -> {
            client.getListener().onReceive(invocation.getArgument(0));
            return STATUS_OK;
        }).when(manager).send(any());
    }

    @Test
    public void testConstants() {
        assertEquals("uprotocol.permission.ACCESS_UBUS", UPClient.PERMISSION_ACCESS_UBUS);
        assertEquals("uprotocol.entity.name", UPClient.META_DATA_ENTITY_NAME);
        assertEquals("uprotocol.entity.version", UPClient.META_DATA_ENTITY_VERSION);
    }

    @Test
    public void testCreate() {
        injectPackage(buildPackageInfo(mPackageName,
                buildServiceInfo(new ComponentName(mPackageName, ".Service"), buildMetadata(SERVICE))));
        assertNotNull(UPClient.create(mContext, SERVICE, mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreateWithoutEntity() {
        assertNotNull(UPClient.create(mContext, mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreateWithEntityId() {
        final UEntity entity = UEntity.newBuilder()
                .setName(CLIENT.getName())
                .setVersionMajor(CLIENT.getVersionMajor())
                .setId(100)
                .build();
        injectPackage(buildPackageInfo(mPackageName, buildMetadata(entity)));
        assertNotNull(UPClient.create(mContext, entity, mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreateWitHandler() {
        assertNotNull(UPClient.create(mContext, mHandler, mServiceLifecycleListener));
    }

    @Test
    public void testCreateWithDefaultCallbackThread() {
        assertNotNull(UPClient.create(mContext, (Handler) null, mServiceLifecycleListener));
        assertNotNull(UPClient.create(mContext, (Executor) null, mServiceLifecycleListener));
    }

    @Test
    public void testCreateWithoutServiceLifecycleListener() {
        assertNotNull(UPClient.create(mContext, mExecutor, null));
    }

    @Test
    public void testCreateWithBadContextWrapper() {
        final ContextWrapper context = spy(new ContextWrapper(mContext));
        doReturn(null).when(context).getBaseContext();
        assertThrows(NullPointerException.class, () -> UPClient.create(context, mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreatePackageManagerNotAvailable() {
        assertThrows(NullPointerException.class, () -> UPClient.create(mock(Context.class), mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreatePackageNotFound() throws NameNotFoundException {
        final PackageManager manager = mock(PackageManager.class);
        doThrow(new NameNotFoundException()).when(manager).getPackageInfo(anyString(), anyInt());
        final Context context = spy(new ContextWrapper(mContext));
        doReturn(manager).when(context).getPackageManager();
        assertThrows(SecurityException.class, () -> UPClient.create(context, mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreateEntityNotDeclared() {
        injectPackage(buildPackageInfo(mPackageName));
        assertThrows(SecurityException.class, () -> UPClient.create(mContext, mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreateEntityNameNotDeclared() {
        injectPackage(buildPackageInfo(mPackageName,
                buildMetadata(UEntity.newBuilder().setVersionMajor(1).build())));
        assertThrows(SecurityException.class, () -> UPClient.create(mContext, mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreateEntityVersionNotDeclared() {
        injectPackage(buildPackageInfo(mPackageName,
                buildMetadata(UEntity.newBuilder().setName(CLIENT.getName()).build())));
        assertThrows(SecurityException.class, () -> UPClient.create(mContext, mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreateVerboseVersionLogged() {
        final String tag = mClient.getTag();
        ShadowLog.setLoggable(tag, Log.VERBOSE);
        assertNotNull(UPClient.create(mContext, mClient.getEntity(), mHandler, mServiceLifecycleListener));
        ShadowLog.getLogsForTag(tag).stream()
                .filter(it -> it.msg.contains(LIBRARY_PACKAGE_NAME) && it.msg.contains(VERSION_NAME))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Version is not printed"));
    }

    @Test
    public void testConnect() {
        final CompletableFuture<UStatus> future = new CompletableFuture<>();
        doReturn(future).when(mManager).connect();
        assertEquals(future, mClient.connect());
    }

    @Test
    public void testDisconnect() {
        final CompletableFuture<UStatus> future = new CompletableFuture<>();
        doReturn(future).when(mManager).disconnect();
        assertEquals(future, mClient.disconnect());
    }

    @Test
    public void testIsDisconnected() {
        assertFalse(mClient.isDisconnected());
        doReturn(true).when(mManager).isDisconnected();
        assertTrue(mClient.isDisconnected());
    }

    @Test
    public void testIsConnecting() {
        assertFalse(mClient.isConnecting());
        doReturn(true).when(mManager).isConnecting();
        assertTrue(mClient.isConnecting());
    }

    @Test
    public void testIsConnected() {
        assertFalse(mClient.isConnected());
        doReturn(true).when(mManager).isConnected();
        assertTrue(mClient.isConnected());
    }

    @Test
    public void testOnConnected() {
        mClient.getConnectionCallback().onConnected();
        verify(mServiceLifecycleListener, times(1)).onLifecycleChanged(mClient, true);
    }

    @Test
    public void testOnDisconnected() {
        mClient.getConnectionCallback().onDisconnected();
        verify(mServiceLifecycleListener, times(1)).onLifecycleChanged(mClient, false);
    }

    @Test
    public void testOnConnectionInterrupted() {
        mClient.getConnectionCallback().onConnectionInterrupted();
        verify(mServiceLifecycleListener, times(1)).onLifecycleChanged(mClient, false);
    }

    @Test
    public void testOnConnectedSuppressed() {
        final UPClient client = new UPClient(mContext, CLIENT, mManager, mExecutor, null);
        client.getConnectionCallback().onConnected();
        verify(mExecutor, times(1)).execute(any());
    }

    @Test
    public void testGetEntity() {
        assertEquals(CLIENT, mClient.getEntity());
    }

    @Test
    public void testGetUri() {
        assertEquals(CLIENT, mClient.getUri().getEntity());
    }

    @Test
    public void testSend() {
        doReturn(STATUS_OK).when(mManager).send(MESSAGE);
        assertStatus(UCode.OK, mClient.send(MESSAGE));
    }

    @Test
    public void testSendParts() {
        doReturn(STATUS_OK).when(mManager).send(MESSAGE);
        assertStatus(UCode.OK, mClient.send(MESSAGE.getSource(), MESSAGE.getPayload(), MESSAGE.getAttributes()));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testSendPartsWithNulls() {
        doReturn(STATUS_OK).when(mManager).send(any());
        assertStatus(UCode.OK, mClient.send(null, MESSAGE.getPayload(), MESSAGE.getAttributes()));
        assertStatus(UCode.OK, mClient.send(MESSAGE.getSource(), null, MESSAGE.getAttributes()));
        assertStatus(UCode.OK, mClient.send(MESSAGE.getSource(), MESSAGE.getPayload(), null));
    }

    @Test
    public void testRegisterListener() {
        doReturn(STATUS_OK).when(mManager).enableDispatching(RESOURCE_URI);
        assertStatus(UCode.OK, mClient.registerListener(RESOURCE_URI, mListener));
        verify(mManager, times(1)).enableDispatching(RESOURCE_URI);
        verify(mManager, never()).getLastMessage(RESOURCE_URI);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testRegisterListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, mClient.registerListener(UUri.getDefaultInstance(), mListener));
        assertStatus(UCode.INVALID_ARGUMENT, mClient.registerListener(RESOURCE_URI, null));
        assertStatus(UCode.INVALID_ARGUMENT, mClient.registerListener(METHOD_URI, mListener));
        verify(mManager, never()).enableDispatching(RESOURCE_URI);
    }

    @Test
    public void testRegisterListenerDifferentTopics() {
        doReturn(STATUS_OK).when(mManager).enableDispatching(RESOURCE_URI);
        doReturn(STATUS_OK).when(mManager).enableDispatching(RESOURCE2_URI);
        assertStatus(UCode.OK, mClient.registerListener(RESOURCE_URI, mListener));
        assertStatus(UCode.OK, mClient.registerListener(RESOURCE2_URI, mListener));
        verify(mManager, times(1)).enableDispatching(RESOURCE_URI);
        verify(mManager, times(1)).enableDispatching(RESOURCE2_URI);
    }

    @Test
    public void testRegisterListenerSame() {
        testRegisterListener();
        assertStatus(UCode.OK, mClient.registerListener(RESOURCE_URI, mListener));
        verify(mManager, times(1)).enableDispatching(RESOURCE_URI);
        verify(mManager, never()).getLastMessage(RESOURCE_URI);
    }

    @Test
    public void testRegisterListenerNotFirst() {
        testRegisterListener();
        assertStatus(UCode.OK, mClient.registerListener(RESOURCE_URI, mListener2));
        verify(mManager, times(1)).enableDispatching(RESOURCE_URI);
        verify(mManager, times(1)).getLastMessage(RESOURCE_URI);
    }

    @Test
    public void testRegisterListenerNotFirstLastMessageNotified() {
        doReturn(MESSAGE).when(mManager).getLastMessage(RESOURCE_URI);
        testRegisterListenerNotFirst();
        verify(mListener2, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
    }

    @Test
    public void testRegisterListenerFailed() {
        doReturn(buildStatus(UCode.UNAUTHENTICATED)).when(mManager).enableDispatching(RESOURCE_URI);
        assertStatus(UCode.UNAUTHENTICATED, mClient.registerListener(RESOURCE_URI, mListener));
    }

    @Test
    public void testRegisterListenerWhenReconnected() {
        testRegisterListener();
        mClient.getConnectionCallback().onConnectionInterrupted();
        verify(mManager, timeout(DELAY_MS).times(0)).disableDispatchingQuietly(RESOURCE_URI);
        mClient.getConnectionCallback().onConnected();
        verify(mManager, timeout(DELAY_MS).times(2)).enableDispatching(RESOURCE_URI);
    }

    @Test
    public void testUnregisterListener() {
        testRegisterListener();
        doReturn(STATUS_OK).when(mManager).disableDispatching(RESOURCE_URI);
        assertStatus(UCode.OK, mClient.unregisterListener(RESOURCE_URI, mListener));
        verify(mManager, times(1)).disableDispatchingQuietly(RESOURCE_URI);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, mClient.unregisterListener(UUri.getDefaultInstance(), mListener));
        assertStatus(UCode.INVALID_ARGUMENT, mClient.unregisterListener(METHOD_URI, mListener));
        assertStatus(UCode.INVALID_ARGUMENT, mClient.unregisterListener(RESOURCE_URI, null));
        verify(mManager, never()).disableDispatchingQuietly(RESOURCE_URI);
    }

    @Test
    public void testUnregisterListenerSame() {
        testUnregisterListener();
        assertStatus(UCode.OK, mClient.unregisterListener(RESOURCE_URI, mListener));
        verify(mManager, times(1)).disableDispatchingQuietly(RESOURCE_URI);
    }

    @Test
    public void testUnregisterListenerNotRegistered() {
        testRegisterListener();
        assertStatus(UCode.OK, mClient.unregisterListener(RESOURCE_URI, mListener2));
        verify(mManager, times(0)).disableDispatchingQuietly(RESOURCE_URI);
    }

    @Test
    public void testUnregisterListenerNotLast() {
        testRegisterListenerNotFirst();
        assertStatus(UCode.OK, mClient.unregisterListener(RESOURCE_URI, mListener));
        verify(mManager, never()).disableDispatchingQuietly(RESOURCE_URI);
    }

    @Test
    public void testUnregisterListenerLast() {
        testUnregisterListenerNotLast();
        assertStatus(UCode.OK, mClient.unregisterListener(RESOURCE_URI, mListener2));
        verify(mManager, times(1)).disableDispatchingQuietly(RESOURCE_URI);
    }

    @Test
    public void testUnregisterListenerWhenDisconnected() {
        testRegisterListener();
        mClient.getConnectionCallback().onDisconnected();
        mClient.getListener().onReceive(MESSAGE);
        verify(mListener, timeout(DELAY_MS).times(0)).onReceive(MESSAGE);
    }

    @Test
    public void testUnregisterListenerFromAllTopics() {
        testRegisterListenerDifferentTopics();
        assertStatus(UCode.OK, mClient.unregisterListener(mListener));
        verify(mManager, times(1)).disableDispatchingQuietly(RESOURCE_URI);
        verify(mManager, times(1)).disableDispatchingQuietly(RESOURCE2_URI);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterListenerFromAllTopicsWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, mClient.unregisterListener(null));
    }

    @Test
    public void testOnReceiveGenericMessage() {
        testRegisterListenerNotFirst();
        mClient.getListener().onReceive(MESSAGE);
        verify(mListener, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
        verify(mListener2, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
    }

    @Test
    public void testOnReceiveGenericMessageParts() {
        testRegisterListenerNotFirst();
        mClient.getListener().onReceive(MESSAGE.getSource(), MESSAGE.getPayload(), MESSAGE.getAttributes());
        verify(mListener, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
        verify(mListener2, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
    }

    @Test
    public void testOnReceiveGenericMessageNotRegistered() {
        testUnregisterListener();
        mClient.getListener().onReceive(MESSAGE);
        verify(mListener, timeout(DELAY_MS).times(0)).onReceive(MESSAGE);
    }

    @Test
    public void testOnReceiveNotificationMessage() {
        testRegisterListener();
        final UMessage message =
                buildMessage(RESOURCE_URI, PAYLOAD,newNotificationAttributesBuilder(CLIENT_URI).build());
        mClient.getListener().onReceive(message);
        verify(mListener, timeout(DELAY_MS).times(1)).onReceive(message);
    }

    @Test
    public void testOnReceiveNotificationMessageWrongSink() {
        mClient.setLoggable(Log.VERBOSE);
        testRegisterListener();
        final UMessage message =
                buildMessage(RESOURCE_URI, PAYLOAD, newNotificationAttributesBuilder(SERVICE_URI).build());
        mClient.getListener().onReceive(message);
        verify(mListener, timeout(DELAY_MS).times(0)).onReceive(message);
    }

    @Test
    public void testOnReceiveMessageExpired() {
        mClient.setLoggable(Log.VERBOSE);
        testRegisterListener();
        final UMessage message = buildMessage(RESOURCE_URI, PAYLOAD, newPublishAttributesBuilder().withTtl(1).build());
        sleep(DELAY_MS);
        mClient.getListener().onReceive(message);
        verify(mListener, timeout(DELAY_MS).times(0)).onReceive(message);
    }

    @Test
    public void testOnReceiveMessageWithoutAttributes() {
        testRegisterListener();
        final UMessage message = buildMessage(RESOURCE_URI, null, null);
        mClient.getListener().onReceive(message);
        verify(mListener, timeout(DELAY_MS).times(0)).onReceive(message);
    }

    @Test
    public void testOnReceiveMessageWithUnknownType() {
        testRegisterListener();
        try (MockedStatic<UAttributesValidator> mockedValidator = mockStatic(UAttributesValidator.class)) {
            final UMessage message = buildMessage(RESOURCE_URI, null, null);
            final UAttributesValidator dummyValidator = new UAttributesValidator() {
                @Override public ValidationResult validateType(UAttributes attributes) {
                    return ValidationResult.success();
                }
            };
            mockedValidator.when(() -> UAttributesValidator.getValidator(message.getAttributes()))
                    .thenReturn(dummyValidator);
            mClient.getListener().onReceive(message);
            verify(mListener, timeout(DELAY_MS).times(0)).onReceive(message);
        }
    }

    @Test
    public void testRegisterRpcListener() {
        doReturn(STATUS_OK).when(mManager).enableDispatching(METHOD_URI);
        assertStatus(UCode.OK, mClient.registerRpcListener(METHOD_URI, mRequestListener));
        verify(mManager, times(1)).enableDispatching(METHOD_URI);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testRegisterRpcListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, mClient.registerRpcListener(UUri.getDefaultInstance(), mRequestListener));
        assertStatus(UCode.INVALID_ARGUMENT, mClient.registerRpcListener(RESOURCE_URI, mRequestListener));
        assertStatus(UCode.INVALID_ARGUMENT, mClient.registerRpcListener(METHOD_URI, null));
        verify(mManager, never()).enableDispatching(METHOD_URI);
    }

    @Test
    public void testRegisterRpcListenerDifferentMethods() {
        doReturn(STATUS_OK).when(mManager).enableDispatching(METHOD_URI);
        doReturn(STATUS_OK).when(mManager).enableDispatching(METHOD2_URI);
        assertStatus(UCode.OK, mClient.registerRpcListener(METHOD_URI, mRequestListener));
        assertStatus(UCode.OK, mClient.registerRpcListener(METHOD2_URI, mRequestListener));
        verify(mManager, times(1)).enableDispatching(METHOD_URI);
        verify(mManager, times(1)).enableDispatching(METHOD2_URI);
    }

    @Test
    public void testRegisterRpcListenerSame() {
        testRegisterRpcListener();
        assertStatus(UCode.OK, mClient.registerRpcListener(METHOD_URI, mRequestListener));
        verify(mManager, times(1)).enableDispatching(METHOD_URI);
    }

    @Test
    public void testRegisterRpcListenerNotFirst() {
        testRegisterRpcListener();
        assertStatus(UCode.ALREADY_EXISTS, mClient.registerRpcListener(METHOD_URI, mRequestListener2));
        verify(mManager, times(1)).enableDispatching(METHOD_URI);
    }

    @Test
    public void testRegisterRpcListenerFailed() {
        doReturn(buildStatus(UCode.UNAUTHENTICATED)).when(mManager).enableDispatching(METHOD_URI);
        assertStatus(UCode.UNAUTHENTICATED, mClient.registerRpcListener(METHOD_URI, mRequestListener));
    }

    @Test
    public void testRegisterRpcListenerWhenReconnected() {
        testRegisterRpcListener();
        mClient.getConnectionCallback().onConnectionInterrupted();
        verify(mManager, timeout(DELAY_MS).times(0)).disableDispatchingQuietly(METHOD_URI);
        mClient.getConnectionCallback().onConnected();
        verify(mManager, timeout(DELAY_MS).times(2)).enableDispatching(METHOD_URI);
    }

    @Test
    public void testUnregisterRpcListener() {
        testRegisterRpcListener();
        doReturn(STATUS_OK).when(mManager).disableDispatching(METHOD_URI);
        assertStatus(UCode.OK, mClient.unregisterRpcListener(METHOD_URI, mRequestListener));
        verify(mManager, times(1)).disableDispatchingQuietly(METHOD_URI);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterRpcListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, mClient.unregisterRpcListener(UUri.getDefaultInstance(), mRequestListener));
        assertStatus(UCode.INVALID_ARGUMENT, mClient.unregisterRpcListener(RESOURCE_URI, mRequestListener));
        assertStatus(UCode.INVALID_ARGUMENT, mClient.unregisterRpcListener(METHOD_URI, null));
        verify(mManager, never()).disableDispatchingQuietly(METHOD_URI);
    }

    @Test
    public void testUnregisterRpcListenerSame() {
        testUnregisterRpcListener();
        assertStatus(UCode.OK, mClient.unregisterRpcListener(METHOD_URI, mRequestListener));
        verify(mManager, times(1)).disableDispatchingQuietly(METHOD_URI);
    }

    @Test
    public void testUnregisterRpcListenerNotRegistered() {
        testRegisterRpcListener();
        assertStatus(UCode.OK, mClient.unregisterRpcListener(METHOD_URI, mRequestListener2));
        verify(mManager, times(0)).disableDispatchingQuietly(METHOD_URI);
    }

    @Test
    public void testUnregisterRpcListenerWhenDisconnected() {
        testRegisterRpcListener();
        mClient.getConnectionCallback().onDisconnected();
        final UMessage requestMessage = buildMessage(RESPONSE_URI, PAYLOAD, buildRequestAttributes(METHOD_URI));
        mClient.getListener().onReceive(requestMessage);
        verify(mRequestListener, timeout(DELAY_MS).times(0)).onReceive(eq(requestMessage), any());
    }

    @Test
    public void testUnregisterRpcListenerFromAllMethods() {
        testRegisterRpcListenerDifferentMethods();
        assertStatus(UCode.OK, mClient.unregisterRpcListener(mRequestListener));
        verify(mManager, times(1)).disableDispatchingQuietly(METHOD_URI);
        verify(mManager, times(1)).disableDispatchingQuietly(METHOD2_URI);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterRpcListenerFromAllMethodsWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, mClient.unregisterRpcListener(null));
    }

    @Test
    public void testUnregisterRpcListenerFromAllMethodsNotRegistered() {
        testRegisterRpcListenerDifferentMethods();
        assertStatus(UCode.OK, mClient.unregisterRpcListener(mRequestListener2));
        verify(mManager, times(0)).disableDispatchingQuietly(METHOD_URI);
        verify(mManager, times(0)).disableDispatchingQuietly(METHOD2_URI);
    }

    @Test
    public void testOnReceiveRequestMessage() {
        testRegisterRpcListener();
        final UMessage requestMessage = buildMessage(RESPONSE_URI, PAYLOAD, buildRequestAttributes(METHOD_URI));
        mClient.getListener().onReceive(requestMessage);
        verify(mRequestListener, timeout(DELAY_MS).times(1)).onReceive(eq(requestMessage), any());
    }

    @Test
    public void testOnReceiveRequestMessageNotRegistered() {
        testUnregisterRpcListener();
        final UMessage requestMessage = buildMessage(RESPONSE_URI, PAYLOAD, buildRequestAttributes(METHOD_URI));
        mClient.getListener().onReceive(requestMessage);
        verify(mRequestListener, timeout(DELAY_MS).times(0)).onReceive(eq(requestMessage), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSendResponseMessage() {
        testRegisterRpcListener();
        final UMessage requestMessage = buildMessage(RESPONSE_URI, PAYLOAD, buildRequestAttributes(METHOD_URI));
        mClient.getListener().onReceive(requestMessage);
        final ArgumentCaptor<CompletableFuture<UPayload>> captor = ArgumentCaptor.forClass(CompletableFuture.class);
        verify(mRequestListener, timeout(DELAY_MS).times(1)).onReceive(eq(requestMessage), captor.capture());
        final CompletableFuture<UPayload> responseFuture = captor.getValue();
        responseFuture.complete(PAYLOAD);
        verify(mManager, times(1)).send(argThat(message -> {
            assertEquals(METHOD_URI, message.getSource());
            assertEquals(PAYLOAD, message.getPayload());
            assertEquals(RESPONSE_URI, message.getAttributes().getSink());
            return true;
        }));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSendResponseMessageWithCommStatus() {
        testRegisterRpcListener();
        final UMessage requestMessage = buildMessage(RESPONSE_URI, PAYLOAD, buildRequestAttributes(METHOD_URI));
        mClient.getListener().onReceive(requestMessage);
        final ArgumentCaptor<CompletableFuture<UPayload>> captor = ArgumentCaptor.forClass(CompletableFuture.class);
        verify(mRequestListener, timeout(DELAY_MS).times(1)).onReceive(eq(requestMessage), captor.capture());
        final CompletableFuture<UPayload> responseFuture = captor.getValue();
        responseFuture.completeExceptionally(new UStatusException(UCode.ABORTED, "Aborted"));
        verify(mManager, times(1)).send(argThat(message -> {
            assertEquals(METHOD_URI, message.getSource());
            assertEquals(UPayload.getDefaultInstance(), message.getPayload());
            assertEquals(RESPONSE_URI, message.getAttributes().getSink());
            assertEquals(UCode.ABORTED_VALUE, message.getAttributes().getCommstatus());
            return true;
        }));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSendResponseMessageNoPayload() {
        testRegisterRpcListener();
        final UMessage requestMessage = buildMessage(RESPONSE_URI, PAYLOAD, buildRequestAttributes(METHOD_URI));
        mClient.getListener().onReceive(requestMessage);
        sleep(DELAY_MS);
        final ArgumentCaptor<CompletableFuture<UPayload>> captor = ArgumentCaptor.forClass(CompletableFuture.class);
        verify(mRequestListener, timeout(DELAY_MS).times(1)).onReceive(eq(requestMessage), captor.capture());
        final CompletableFuture<UPayload> responseFuture = captor.getValue();
        responseFuture.complete(null);
        verify(mManager, timeout(DELAY_MS).times(0)).send(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInvokeMethod() throws Exception {
        testRegisterRpcListener();
        redirectMessages(mManager, mClient);

        final CompletableFuture<UMessage> responseFuture =
                mClient.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, OPTIONS).toCompletableFuture();
        assertFalse(responseFuture.isDone());

        final ArgumentCaptor<UMessage> requestCaptor = ArgumentCaptor.forClass(UMessage.class);
        final ArgumentCaptor<CompletableFuture<UPayload>> responseFutureCaptor =
                ArgumentCaptor.forClass(CompletableFuture.class);
        verify(mRequestListener, timeout(DELAY_MS).times(1))
                .onReceive(requestCaptor.capture(), responseFutureCaptor.capture());
        final UMessage requestMessage = requestCaptor.getValue();
        assertEquals(RESPONSE_URI, requestMessage.getSource());
        assertEquals(REQUEST_PAYLOAD, requestMessage.getPayload());
        assertEquals(METHOD_URI, requestMessage.getAttributes().getSink());
        assertEquals(OPTIONS.timeout(), requestMessage.getAttributes().getTtl());
        assertEquals(UMessageType.UMESSAGE_TYPE_REQUEST, requestMessage.getAttributes().getType());
        responseFutureCaptor.getValue().complete(RESPONSE_PAYLOAD);

        final UMessage responseMessage = responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS);
        assertEquals(METHOD_URI, responseMessage.getSource());
        assertEquals(RESPONSE_PAYLOAD, responseMessage.getPayload());
        assertEquals(RESPONSE_URI, responseMessage.getAttributes().getSink());
        assertEquals(UMessageType.UMESSAGE_TYPE_RESPONSE, responseMessage.getAttributes().getType());
        assertEquals(requestMessage.getAttributes().getId(), responseMessage.getAttributes().getReqid());
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testInvokeMethodWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, toStatus(assertThrows(ExecutionException.class,
                () -> mClient.invokeMethod(null, PAYLOAD, OPTIONS).toCompletableFuture().get())));
        assertStatus(UCode.INVALID_ARGUMENT, toStatus(assertThrows(ExecutionException.class,
                () -> mClient.invokeMethod(UUri.getDefaultInstance(), PAYLOAD, OPTIONS).toCompletableFuture().get())));
        assertStatus(UCode.INVALID_ARGUMENT, toStatus(assertThrows(ExecutionException.class,
                () -> mClient.invokeMethod(METHOD_URI, null, OPTIONS).toCompletableFuture().get())));
        assertStatus(UCode.INVALID_ARGUMENT, toStatus(assertThrows(ExecutionException.class,
                () -> mClient.invokeMethod(METHOD_URI, PAYLOAD, null).toCompletableFuture().get())));
    }

    @Test
    public void testInvokeMethodOtherResponseReceive() {
        testRegisterRpcListener();
        redirectMessages(mManager, mClient);

        final CompletableFuture<UMessage> responseFuture =
                mClient.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, OPTIONS).toCompletableFuture();
        assertFalse(responseFuture.isDone());

        verify(mRequestListener, timeout(DELAY_MS).times(1)).onReceive(any(), any());
        final UMessage responseMessage =
                buildMessage(METHOD_URI, PAYLOAD, buildResponseAttributes(RESPONSE_URI, createId()));
        mClient.getListener().onReceive(responseMessage);

        assertThrows(TimeoutException.class, () -> responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS));
        assertFalse(responseFuture.isDone());
    }

    @Test
    public void testInvokeMethodWhenDisconnected() {
        testRegisterRpcListener();
        redirectMessages(mManager, mClient);

        final CompletableFuture<UMessage> responseFuture =
                mClient.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, OPTIONS).toCompletableFuture();
        assertFalse(responseFuture.isDone());

        verify(mRequestListener, timeout(DELAY_MS).times(1)).onReceive(any(), any());
        final UMessage responseMessage =
                buildMessage(METHOD_URI, PAYLOAD, buildResponseAttributes(RESPONSE_URI, createId()));
        mClient.getListener().onReceive(responseMessage);

        testOnDisconnected();
        assertStatus(UCode.CANCELLED, toStatus(assertThrows(
                ExecutionException.class, () -> responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS))));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInvokeMethodCompletedWithCommStatus() {
        testRegisterRpcListener();
        redirectMessages(mManager, mClient);

        final CompletableFuture<UMessage> responseFuture =
                mClient.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, OPTIONS).toCompletableFuture();
        assertFalse(responseFuture.isDone());

        final ArgumentCaptor<CompletableFuture<UPayload>> responseFutureCaptor =
                ArgumentCaptor.forClass(CompletableFuture.class);
        verify(mRequestListener, timeout(DELAY_MS).times(1)).onReceive(any(), responseFutureCaptor.capture());
        responseFutureCaptor.getValue().completeExceptionally(new UStatusException(UCode.CANCELLED, "Cancelled"));
        assertStatus(UCode.CANCELLED, toStatus(assertThrows(
                ExecutionException.class, () -> responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS))));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInvokeMethodCompletedWithCommStatusOk() throws Exception {
        testRegisterRpcListener();
        redirectMessages(mManager, mClient);

        final CompletableFuture<UMessage> responseFuture =
                mClient.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, OPTIONS).toCompletableFuture();
        assertFalse(responseFuture.isDone());

        final ArgumentCaptor<CompletableFuture<UPayload>> responseFutureCaptor =
                ArgumentCaptor.forClass(CompletableFuture.class);
        verify(mRequestListener, timeout(DELAY_MS).times(1)).onReceive(any(), responseFutureCaptor.capture());
        responseFutureCaptor.getValue().completeExceptionally(new UStatusException(UCode.OK, "No error"));

        final UMessage responseMessage = responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS);
        assertEquals(METHOD_URI, responseMessage.getSource());
        assertEquals(UPayload.getDefaultInstance(), responseMessage.getPayload());
        assertEquals(RESPONSE_URI, responseMessage.getAttributes().getSink());
        assertEquals(UMessageType.UMESSAGE_TYPE_RESPONSE, responseMessage.getAttributes().getType());
    }

    @Test
    public void testInvokeMethodSameRequest() {
        doReturn(buildStatus(UCode.OK)).when(mManager).send(any());
        final UAttributesBuilder builder = UAttributesBuilder.request(UPriority.UPRIORITY_CS4, METHOD_URI, TTL);
        try (MockedStatic<UAttributesBuilder> mockedBuilder = mockStatic(UAttributesBuilder.class)) {
            mockedBuilder.when(() -> UAttributesBuilder.request(UPriority.UPRIORITY_CS4, METHOD_URI, TTL))
                    .thenReturn(builder);
            mClient.invokeMethod(METHOD_URI, PAYLOAD, OPTIONS);
            assertStatus(UCode.ABORTED, toStatus(assertThrows(ExecutionException.class,
                    () -> mClient.invokeMethod(METHOD_URI, PAYLOAD, OPTIONS).toCompletableFuture().get())));
        }
    }

    @Test
    public void testInvokeMethodSendFailure() {
        doReturn(buildStatus(UCode.UNAVAILABLE)).when(mManager).send(any());
        assertStatus(UCode.UNAVAILABLE, toStatus(assertThrows(ExecutionException.class,
                () -> mClient.invokeMethod(METHOD_URI, PAYLOAD, OPTIONS).toCompletableFuture().get())));
    }
}
