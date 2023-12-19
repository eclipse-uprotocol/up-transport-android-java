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

import org.eclipse.uprotocol.ULink.ServiceLifecycleListener;
import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.core.ubus.UBusClient;
import org.eclipse.uprotocol.rpc.CallOptions;
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
public class ULinkTest extends TestBase {
    private static final UMessage MESSAGE = buildMessage(RESOURCE_URI, PAYLOAD, buildPublishAttributes());
    private static final CallOptions OPTIONS = CallOptions.newBuilder()
            .withTimeout(TTL)
            .withToken(TOKEN)
            .build();
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
    private UBusClient mClient;
    private ULink mLink;

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
        mClient = mock(UBusClient.class);
        injectPackage(buildPackageInfo(mPackageName, buildMetadata(CLIENT)));
        mLink = new ULink(mContext, CLIENT, mClient, mExecutor, mServiceLifecycleListener);
        mLink.setLoggable(Log.INFO);
    }

    private void injectPackage(@NonNull PackageInfo packageInfo) {
        mShadowPackageManager.installPackage(packageInfo);
    }

    private static void redirectMessages(@NonNull UBusClient fromClient, @NonNull ULink toLink) {
        doAnswer(invocation -> {
            toLink.getListener().onReceive(invocation.getArgument(0));
            return STATUS_OK;
        }).when(fromClient).send(any());
    }

    @Test
    public void testConstants() {
        assertEquals("uprotocol.permission.ACCESS_UBUS", ULink.PERMISSION_ACCESS_UBUS);
        assertEquals("uprotocol.entity.name", ULink.META_DATA_ENTITY_NAME);
        assertEquals("uprotocol.entity.version", ULink.META_DATA_ENTITY_VERSION);
    }

    @Test
    public void testCreate() {
        injectPackage(buildPackageInfo(mPackageName,
                buildServiceInfo(new ComponentName(mPackageName, ".Service"), buildMetadata(SERVICE))));
        assertNotNull(ULink.create(mContext, SERVICE, mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreateWithoutEntity() {
        assertNotNull(ULink.create(mContext, mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreateWithEntityId() {
        final UEntity entity = UEntity.newBuilder()
                .setName(CLIENT.getName())
                .setVersionMajor(CLIENT.getVersionMajor())
                .setId(100)
                .build();
        injectPackage(buildPackageInfo(mPackageName, buildMetadata(entity)));
        assertNotNull(ULink.create(mContext, entity, mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreateWitHandler() {
        assertNotNull(ULink.create(mContext, mHandler, mServiceLifecycleListener));
    }

    @Test
    public void testCreateWithDefaultCallbackThread() {
        assertNotNull(ULink.create(mContext, (Handler) null, mServiceLifecycleListener));
        assertNotNull(ULink.create(mContext, (Executor) null, mServiceLifecycleListener));
    }

    @Test
    public void testCreateWithoutServiceLifecycleListener() {
        assertNotNull(ULink.create(mContext, mExecutor, null));
    }

    @Test
    public void testCreateWithBadContextWrapper() {
        final ContextWrapper context = spy(new ContextWrapper(mContext));
        doReturn(null).when(context).getBaseContext();
        assertThrows(NullPointerException.class, () -> ULink.create(context, mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreatePackageManagerNotAvailable() {
        assertThrows(NullPointerException.class, () -> ULink.create(mock(Context.class), mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreatePackageNotFound() throws NameNotFoundException {
        final PackageManager manager = mock(PackageManager.class);
        doThrow(new NameNotFoundException()).when(manager).getPackageInfo(anyString(), anyInt());
        final Context context = spy(new ContextWrapper(mContext));
        doReturn(manager).when(context).getPackageManager();
        assertThrows(SecurityException.class, () -> ULink.create(context, mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreateEntityNotDeclared() {
        injectPackage(buildPackageInfo(mPackageName));
        assertThrows(SecurityException.class, () -> ULink.create(mContext, mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreateEntityNameNotDeclared() {
        injectPackage(buildPackageInfo(mPackageName,
                buildMetadata(UEntity.newBuilder().setVersionMajor(1).build())));
        assertThrows(SecurityException.class, () -> ULink.create(mContext, mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreateEntityVersionNotDeclared() {
        injectPackage(buildPackageInfo(mPackageName,
                buildMetadata(UEntity.newBuilder().setName(CLIENT.getName()).build())));
        assertThrows(SecurityException.class, () -> ULink.create(mContext, mExecutor, mServiceLifecycleListener));
    }

    @Test
    public void testCreateVerboseVersionLogged() {
        final String tag = mLink.getTag();
        ShadowLog.setLoggable(tag, Log.VERBOSE);
        assertNotNull(ULink.create(mContext, mLink.getEntity(), mHandler, mServiceLifecycleListener));
        ShadowLog.getLogsForTag(tag).stream()
                .filter(it -> it.msg.contains(LIBRARY_PACKAGE_NAME) && it.msg.contains(VERSION_NAME))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Version is not printed"));
    }

    @Test
    public void testConnect() {
        final CompletableFuture<UStatus> future = new CompletableFuture<>();
        doReturn(future).when(mClient).connect();
        assertEquals(future, mLink.connect());
    }

    @Test
    public void testDisconnect() {
        final CompletableFuture<UStatus> future = new CompletableFuture<>();
        doReturn(future).when(mClient).disconnect();
        assertEquals(future, mLink.disconnect());
    }

    @Test
    public void testIsDisconnected() {
        assertFalse(mLink.isDisconnected());
        doReturn(true).when(mClient).isDisconnected();
        assertTrue(mLink.isDisconnected());
    }

    @Test
    public void testIsConnecting() {
        assertFalse(mLink.isConnecting());
        doReturn(true).when(mClient).isConnecting();
        assertTrue(mLink.isConnecting());
    }

    @Test
    public void testIsConnected() {
        assertFalse(mLink.isConnected());
        doReturn(true).when(mClient).isConnected();
        assertTrue(mLink.isConnected());
    }

    @Test
    public void testOnConnected() {
        mLink.getConnectionCallback().onConnected();
        verify(mServiceLifecycleListener, times(1)).onLifecycleChanged(mLink, true);
    }

    @Test
    public void testOnDisconnected() {
        mLink.getConnectionCallback().onDisconnected();
        verify(mServiceLifecycleListener, times(1)).onLifecycleChanged(mLink, false);
    }

    @Test
    public void testOnConnectionInterrupted() {
        mLink.getConnectionCallback().onConnectionInterrupted();
        verify(mServiceLifecycleListener, times(1)).onLifecycleChanged(mLink, false);
    }

    @Test
    public void testOnConnectedSuppressed() {
        final ULink link = new ULink(mContext, CLIENT, mClient, mExecutor, null);
        link.getConnectionCallback().onConnected();
        verify(mExecutor, times(1)).execute(any());
    }

    @Test
    public void testGetEntity() {
        assertEquals(CLIENT, mLink.getEntity());
    }

    @Test
    public void testGetClientUri() {
        assertEquals(CLIENT, mLink.getClientUri().getEntity());
    }

    @Test
    public void testSend() {
        doReturn(STATUS_OK).when(mClient).send(MESSAGE);
        assertStatus(UCode.OK, mLink.send(MESSAGE));
    }

    @Test
    public void testSendParts() {
        doReturn(STATUS_OK).when(mClient).send(MESSAGE);
        assertStatus(UCode.OK, mLink.send(MESSAGE.getSource(), MESSAGE.getPayload(), MESSAGE.getAttributes()));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testSendPartsWithNulls() {
        doReturn(STATUS_OK).when(mClient).send(any());
        assertStatus(UCode.OK, mLink.send(null, MESSAGE.getPayload(), MESSAGE.getAttributes()));
        assertStatus(UCode.OK, mLink.send(MESSAGE.getSource(), null, MESSAGE.getAttributes()));
        assertStatus(UCode.OK, mLink.send(MESSAGE.getSource(), MESSAGE.getPayload(), null));
    }

    @Test
    public void testRegisterListener() {
        doReturn(STATUS_OK).when(mClient).enableDispatching(RESOURCE_URI);
        assertStatus(UCode.OK, mLink.registerListener(RESOURCE_URI, mListener));
        verify(mClient, times(1)).enableDispatching(RESOURCE_URI);
        verify(mClient, never()).getLastMessage(RESOURCE_URI);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testRegisterListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, mLink.registerListener(UUri.getDefaultInstance(), mListener));
        assertStatus(UCode.INVALID_ARGUMENT, mLink.registerListener(RESOURCE_URI, null));
        verify(mClient, never()).enableDispatching(RESOURCE_URI);
    }

    @Test
    public void testRegisterListenerDifferentTopics() {
        doReturn(STATUS_OK).when(mClient).enableDispatching(RESOURCE_URI);
        doReturn(STATUS_OK).when(mClient).enableDispatching(RESOURCE2_URI);
        assertStatus(UCode.OK, mLink.registerListener(RESOURCE_URI, mListener));
        assertStatus(UCode.OK, mLink.registerListener(RESOURCE2_URI, mListener));
        verify(mClient, times(1)).enableDispatching(RESOURCE_URI);
        verify(mClient, times(1)).enableDispatching(RESOURCE2_URI);
    }

    @Test
    public void testRegisterListenerSame() {
        testRegisterListener();
        assertStatus(UCode.OK, mLink.registerListener(RESOURCE_URI, mListener));
        verify(mClient, times(1)).enableDispatching(RESOURCE_URI);
        verify(mClient, never()).getLastMessage(RESOURCE_URI);
    }

    @Test
    public void testRegisterListenerNotFirst() {
        testRegisterListener();
        assertStatus(UCode.OK, mLink.registerListener(RESOURCE_URI, mListener2));
        verify(mClient, times(1)).enableDispatching(RESOURCE_URI);
        verify(mClient, times(1)).getLastMessage(RESOURCE_URI);
    }

    @Test
    public void testRegisterListenerNotFirstLastMessageNotified() {
        doReturn(MESSAGE).when(mClient).getLastMessage(RESOURCE_URI);
        testRegisterListenerNotFirst();
        verify(mListener2, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
    }

    @Test
    public void testRegisterListenerFailed() {
        doReturn(buildStatus(UCode.UNAUTHENTICATED)).when(mClient).enableDispatching(RESOURCE_URI);
        assertStatus(UCode.UNAUTHENTICATED, mLink.registerListener(RESOURCE_URI, mListener));
    }

    @Test
    public void testRegisterListenerWhenReconnected() {
        testRegisterListener();
        mLink.getConnectionCallback().onConnectionInterrupted();
        verify(mClient, timeout(DELAY_MS).times(0)).disableDispatchingQuietly(RESOURCE_URI);
        mLink.getConnectionCallback().onConnected();
        verify(mClient, timeout(DELAY_MS).times(2)).enableDispatching(RESOURCE_URI);
    }

    @Test
    public void testUnregisterListener() {
        testRegisterListener();
        doReturn(STATUS_OK).when(mClient).disableDispatching(RESOURCE_URI);
        assertStatus(UCode.OK, mLink.unregisterListener(RESOURCE_URI, mListener));
        verify(mClient, times(1)).disableDispatchingQuietly(RESOURCE_URI);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, mLink.unregisterListener(UUri.getDefaultInstance(), mListener));
        assertStatus(UCode.INVALID_ARGUMENT, mLink.unregisterListener(RESOURCE_URI, null));
        verify(mClient, never()).disableDispatchingQuietly(RESOURCE_URI);
    }

    @Test
    public void testUnregisterListenerSame() {
        testUnregisterListener();
        assertStatus(UCode.OK, mLink.unregisterListener(RESOURCE_URI, mListener));
        verify(mClient, times(1)).disableDispatchingQuietly(RESOURCE_URI);
    }

    @Test
    public void testUnregisterListenerNotRegistered() {
        testRegisterListener();
        assertStatus(UCode.OK, mLink.unregisterListener(RESOURCE_URI, mListener2));
        verify(mClient, times(0)).disableDispatchingQuietly(RESOURCE_URI);
    }

    @Test
    public void testUnregisterListenerNotLast() {
        testRegisterListenerNotFirst();
        assertStatus(UCode.OK, mLink.unregisterListener(RESOURCE_URI, mListener));
        verify(mClient, never()).disableDispatchingQuietly(RESOURCE_URI);
    }

    @Test
    public void testUnregisterListenerLast() {
        testUnregisterListenerNotLast();
        assertStatus(UCode.OK, mLink.unregisterListener(RESOURCE_URI, mListener2));
        verify(mClient, times(1)).disableDispatchingQuietly(RESOURCE_URI);
    }

    @Test
    public void testUnregisterListenerWhenDisconnected() {
        testRegisterListener();
        mLink.getConnectionCallback().onDisconnected();
        mLink.getListener().onReceive(MESSAGE);
        verify(mListener, timeout(DELAY_MS).times(0)).onReceive(MESSAGE);
    }

    @Test
    public void testUnregisterListenerFromAllTopics() {
        testRegisterListenerDifferentTopics();
        assertStatus(UCode.OK, mLink.unregisterListener(mListener));
        verify(mClient, times(1)).disableDispatchingQuietly(RESOURCE_URI);
        verify(mClient, times(1)).disableDispatchingQuietly(RESOURCE2_URI);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterListenerFromAllTopicsWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, mLink.unregisterListener(null));
    }

    @Test
    public void testOnReceiveGenericMessage() {
        testRegisterListenerNotFirst();
        mLink.getListener().onReceive(MESSAGE);
        verify(mListener, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
        verify(mListener2, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
    }

    @Test
    public void testOnReceiveGenericMessageParts() {
        testRegisterListenerNotFirst();
        mLink.getListener().onReceive(MESSAGE.getSource(), MESSAGE.getPayload(), MESSAGE.getAttributes());
        verify(mListener, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
        verify(mListener2, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
    }

    @Test
    public void testOnReceiveGenericMessageNotRegistered() {
        testUnregisterListener();
        mLink.getListener().onReceive(MESSAGE);
        verify(mListener, timeout(DELAY_MS).times(0)).onReceive(MESSAGE);
    }

    @Test
    public void testOnReceiveNotificationMessage() {
        testRegisterListener();
        final UMessage message =
                buildMessage(RESOURCE_URI, PAYLOAD,newNotificationAttributesBuilder(CLIENT_URI).build());
        mLink.getListener().onReceive(message);
        verify(mListener, timeout(DELAY_MS).times(1)).onReceive(message);
    }

    @Test
    public void testOnReceiveNotificationMessageWrongSink() {
        mLink.setLoggable(Log.VERBOSE);
        testRegisterListener();
        final UMessage message =
                buildMessage(RESOURCE_URI, PAYLOAD, newNotificationAttributesBuilder(SERVICE_URI).build());
        mLink.getListener().onReceive(message);
        verify(mListener, timeout(DELAY_MS).times(0)).onReceive(message);
    }

    @Test
    public void testOnReceiveMessageExpired() {
        mLink.setLoggable(Log.VERBOSE);
        testRegisterListener();
        final UMessage message = buildMessage(RESOURCE_URI, PAYLOAD, newPublishAttributesBuilder().withTtl(1).build());
        sleep(DELAY_MS);
        mLink.getListener().onReceive(message);
        verify(mListener, timeout(DELAY_MS).times(0)).onReceive(message);
    }

    @Test
    public void testOnReceiveMessageWithoutAttributes() {
        testRegisterListener();
        final UMessage message = buildMessage(RESOURCE_URI, null, null);
        mLink.getListener().onReceive(message);
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
            mLink.getListener().onReceive(message);
            verify(mListener, timeout(DELAY_MS).times(0)).onReceive(message);
        }
    }

    @Test
    public void testRegisterRpcListener() {
        doReturn(STATUS_OK).when(mClient).enableDispatching(METHOD_URI);
        assertStatus(UCode.OK, mLink.registerRpcListener(METHOD_URI, mRequestListener));
        verify(mClient, times(1)).enableDispatching(METHOD_URI);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testRegisterRpcListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, mLink.registerRpcListener(UUri.getDefaultInstance(), mRequestListener));
        assertStatus(UCode.INVALID_ARGUMENT, mLink.registerRpcListener(METHOD_URI, null));
        verify(mClient, never()).enableDispatching(METHOD_URI);
    }

    @Test
    public void testRegisterRpcListenerDifferentMethods() {
        doReturn(STATUS_OK).when(mClient).enableDispatching(METHOD_URI);
        doReturn(STATUS_OK).when(mClient).enableDispatching(METHOD2_URI);
        assertStatus(UCode.OK, mLink.registerRpcListener(METHOD_URI, mRequestListener));
        assertStatus(UCode.OK, mLink.registerRpcListener(METHOD2_URI, mRequestListener));
        verify(mClient, times(1)).enableDispatching(METHOD_URI);
        verify(mClient, times(1)).enableDispatching(METHOD2_URI);
    }

    @Test
    public void testRegisterRpcListenerSame() {
        testRegisterRpcListener();
        assertStatus(UCode.OK, mLink.registerRpcListener(METHOD_URI, mRequestListener));
        verify(mClient, times(1)).enableDispatching(METHOD_URI);
    }

    @Test
    public void testRegisterRpcListenerNotFirst() {
        testRegisterRpcListener();
        assertStatus(UCode.ALREADY_EXISTS, mLink.registerRpcListener(METHOD_URI, mRequestListener2));
        verify(mClient, times(1)).enableDispatching(METHOD_URI);
    }

    @Test
    public void testRegisterRpcListenerFailed() {
        doReturn(buildStatus(UCode.UNAUTHENTICATED)).when(mClient).enableDispatching(METHOD_URI);
        assertStatus(UCode.UNAUTHENTICATED, mLink.registerRpcListener(METHOD_URI, mRequestListener));
    }

    @Test
    public void testRegisterRpcListenerWhenReconnected() {
        testRegisterRpcListener();
        mLink.getConnectionCallback().onConnectionInterrupted();
        verify(mClient, timeout(DELAY_MS).times(0)).disableDispatchingQuietly(METHOD_URI);
        mLink.getConnectionCallback().onConnected();
        verify(mClient, timeout(DELAY_MS).times(2)).enableDispatching(METHOD_URI);
    }

    @Test
    public void testUnregisterRpcListener() {
        testRegisterRpcListener();
        doReturn(STATUS_OK).when(mClient).disableDispatching(METHOD_URI);
        assertStatus(UCode.OK, mLink.unregisterRpcListener(METHOD_URI, mRequestListener));
        verify(mClient, times(1)).disableDispatchingQuietly(METHOD_URI);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterRpcListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, mLink.unregisterRpcListener(UUri.getDefaultInstance(), mRequestListener));
        assertStatus(UCode.INVALID_ARGUMENT, mLink.unregisterRpcListener(METHOD_URI, null));
        verify(mClient, never()).disableDispatchingQuietly(METHOD_URI);
    }

    @Test
    public void testUnregisterRpcListenerSame() {
        testUnregisterRpcListener();
        assertStatus(UCode.OK, mLink.unregisterRpcListener(METHOD_URI, mRequestListener));
        verify(mClient, times(1)).disableDispatchingQuietly(METHOD_URI);
    }

    @Test
    public void testUnregisterRpcListenerNotRegistered() {
        testRegisterRpcListener();
        assertStatus(UCode.OK, mLink.unregisterRpcListener(METHOD_URI, mRequestListener2));
        verify(mClient, times(0)).disableDispatchingQuietly(METHOD_URI);
    }

    @Test
    public void testUnregisterRpcListenerWhenDisconnected() {
        testRegisterRpcListener();
        mLink.getConnectionCallback().onDisconnected();
        final UMessage requestMessage = buildMessage(RESPONSE_URI, PAYLOAD, buildRequestAttributes(METHOD_URI));
        mLink.getListener().onReceive(requestMessage);
        verify(mRequestListener, timeout(DELAY_MS).times(0)).onReceive(eq(requestMessage), any());
    }

    @Test
    public void testUnregisterRpcListenerFromAllMethods() {
        testRegisterRpcListenerDifferentMethods();
        assertStatus(UCode.OK, mLink.unregisterRpcListener(mRequestListener));
        verify(mClient, times(1)).disableDispatchingQuietly(METHOD_URI);
        verify(mClient, times(1)).disableDispatchingQuietly(METHOD2_URI);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterRpcListenerFromAllMethodsWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, mLink.unregisterRpcListener(null));
    }

    @Test
    public void testUnregisterRpcListenerFromAllMethodsNotRegistered() {
        testRegisterRpcListenerDifferentMethods();
        assertStatus(UCode.OK, mLink.unregisterRpcListener(mRequestListener2));
        verify(mClient, times(0)).disableDispatchingQuietly(METHOD_URI);
        verify(mClient, times(0)).disableDispatchingQuietly(METHOD2_URI);
    }

    @Test
    public void testOnReceiveRequestMessage() {
        testRegisterRpcListener();
        final UMessage requestMessage = buildMessage(RESPONSE_URI, PAYLOAD, buildRequestAttributes(METHOD_URI));
        mLink.getListener().onReceive(requestMessage);
        verify(mRequestListener, timeout(DELAY_MS).times(1)).onReceive(eq(requestMessage), any());
    }

    @Test
    public void testOnReceiveRequestMessageNotRegistered() {
        testUnregisterRpcListener();
        final UMessage requestMessage = buildMessage(RESPONSE_URI, PAYLOAD, buildRequestAttributes(METHOD_URI));
        mLink.getListener().onReceive(requestMessage);
        verify(mRequestListener, timeout(DELAY_MS).times(0)).onReceive(eq(requestMessage), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSendResponseMessage() {
        testRegisterRpcListener();
        final UMessage requestMessage = buildMessage(RESPONSE_URI, PAYLOAD, buildRequestAttributes(METHOD_URI));
        mLink.getListener().onReceive(requestMessage);
        final ArgumentCaptor<CompletableFuture<UPayload>> captor = ArgumentCaptor.forClass(CompletableFuture.class);
        verify(mRequestListener, timeout(DELAY_MS).times(1)).onReceive(eq(requestMessage), captor.capture());
        final CompletableFuture<UPayload> responseFuture = captor.getValue();
        responseFuture.complete(PAYLOAD);
        verify(mClient, times(1)).send(argThat(message -> {
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
        mLink.getListener().onReceive(requestMessage);
        final ArgumentCaptor<CompletableFuture<UPayload>> captor = ArgumentCaptor.forClass(CompletableFuture.class);
        verify(mRequestListener, timeout(DELAY_MS).times(1)).onReceive(eq(requestMessage), captor.capture());
        final CompletableFuture<UPayload> responseFuture = captor.getValue();
        responseFuture.completeExceptionally(new UStatusException(UCode.ABORTED, "Aborted"));
        verify(mClient, times(1)).send(argThat(message -> {
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
        mLink.getListener().onReceive(requestMessage);
        sleep(DELAY_MS);
        final ArgumentCaptor<CompletableFuture<UPayload>> captor = ArgumentCaptor.forClass(CompletableFuture.class);
        verify(mRequestListener, timeout(DELAY_MS).times(1)).onReceive(eq(requestMessage), captor.capture());
        final CompletableFuture<UPayload> responseFuture = captor.getValue();
        responseFuture.complete(null);
        verify(mClient, timeout(DELAY_MS).times(0)).send(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInvokeMethod() throws Exception {
        testRegisterRpcListener();
        redirectMessages(mClient, mLink);

        final CompletableFuture<UPayload> responseFuture =
                mLink.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, OPTIONS).toCompletableFuture();
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

        assertEquals(RESPONSE_PAYLOAD, responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testInvokeMethodWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, toStatus(assertThrows(ExecutionException.class,
                () -> mLink.invokeMethod(null, PAYLOAD, OPTIONS).toCompletableFuture().get())));
        assertStatus(UCode.INVALID_ARGUMENT, toStatus(assertThrows(ExecutionException.class,
                () -> mLink.invokeMethod(UUri.getDefaultInstance(), PAYLOAD, OPTIONS).toCompletableFuture().get())));
        assertStatus(UCode.INVALID_ARGUMENT, toStatus(assertThrows(ExecutionException.class,
                () -> mLink.invokeMethod(METHOD_URI, null, OPTIONS).toCompletableFuture().get())));
        assertStatus(UCode.INVALID_ARGUMENT, toStatus(assertThrows(ExecutionException.class,
                () -> mLink.invokeMethod(METHOD_URI, PAYLOAD, null).toCompletableFuture().get())));
    }

    @Test
    public void testInvokeMethodOtherResponseReceive() {
        testRegisterRpcListener();
        redirectMessages(mClient, mLink);

        final CompletableFuture<UPayload> responseFuture =
                mLink.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, OPTIONS).toCompletableFuture();
        assertFalse(responseFuture.isDone());

        verify(mRequestListener, timeout(DELAY_MS).times(1)).onReceive(any(), any());
        final UMessage responseMessage =
                buildMessage(METHOD_URI, PAYLOAD, buildResponseAttributes(RESPONSE_URI, createId()));
        mLink.getListener().onReceive(responseMessage);

        assertThrows(TimeoutException.class, () -> responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS));
        assertFalse(responseFuture.isDone());
    }

    @Test
    public void testInvokeMethodWhenDisconnected() {
        testRegisterRpcListener();
        redirectMessages(mClient, mLink);

        final CompletableFuture<UPayload> responseFuture =
                mLink.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, OPTIONS).toCompletableFuture();
        assertFalse(responseFuture.isDone());

        verify(mRequestListener, timeout(DELAY_MS).times(1)).onReceive(any(), any());
        final UMessage responseMessage =
                buildMessage(METHOD_URI, PAYLOAD, buildResponseAttributes(RESPONSE_URI, createId()));
        mLink.getListener().onReceive(responseMessage);

        testOnDisconnected();
        assertStatus(UCode.CANCELLED, toStatus(assertThrows(
                ExecutionException.class, () -> responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS))));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInvokeMethodCompletedWithCommStatus() {
        testRegisterRpcListener();
        redirectMessages(mClient, mLink);

        final CompletableFuture<UPayload> responseFuture =
                mLink.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, OPTIONS).toCompletableFuture();
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
        redirectMessages(mClient, mLink);

        final CompletableFuture<UPayload> responseFuture =
                mLink.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, OPTIONS).toCompletableFuture();
        assertFalse(responseFuture.isDone());

        final ArgumentCaptor<CompletableFuture<UPayload>> responseFutureCaptor =
                ArgumentCaptor.forClass(CompletableFuture.class);
        verify(mRequestListener, timeout(DELAY_MS).times(1)).onReceive(any(), responseFutureCaptor.capture());
        responseFutureCaptor.getValue().completeExceptionally(new UStatusException(UCode.OK, "No error"));
        assertEquals(UPayload.getDefaultInstance(), responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testInvokeMethodSameRequest() {
        doReturn(buildStatus(UCode.OK)).when(mClient).send(any());
        final UAttributesBuilder builder = UAttributesBuilder.request(UPriority.UPRIORITY_CS4, METHOD_URI, TTL);
        try (MockedStatic<UAttributesBuilder> mockedBuilder = mockStatic(UAttributesBuilder.class)) {
            mockedBuilder.when(() -> UAttributesBuilder.request(UPriority.UPRIORITY_CS4, METHOD_URI, TTL))
                    .thenReturn(builder);
            mLink.invokeMethod(METHOD_URI, PAYLOAD, OPTIONS);
            assertStatus(UCode.ABORTED, toStatus(assertThrows(ExecutionException.class,
                    () -> mLink.invokeMethod(METHOD_URI, PAYLOAD, OPTIONS).toCompletableFuture().get())));
        }
    }

    @Test
    public void testInvokeMethodSendFailure() {
        doReturn(buildStatus(UCode.UNAVAILABLE)).when(mClient).send(any());
        assertStatus(UCode.UNAVAILABLE, toStatus(assertThrows(ExecutionException.class,
                () -> mLink.invokeMethod(METHOD_URI, PAYLOAD, OPTIONS).toCompletableFuture().get())));
    }
}
