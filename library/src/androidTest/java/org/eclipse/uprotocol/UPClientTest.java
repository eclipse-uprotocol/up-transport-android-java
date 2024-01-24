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

import static junit.framework.TestCase.assertEquals;

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.protobuf.Int32Value;

import org.eclipse.uprotocol.UPClient.ServiceLifecycleListener;
import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.core.usubscription.v3.CreateTopicRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionResponse;
import org.eclipse.uprotocol.core.usubscription.v3.USubscription;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeRequest;
import org.eclipse.uprotocol.rpc.CallOptions;
import org.eclipse.uprotocol.rpc.URpcListener;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UMessageType;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class UPClientTest extends TestBase {
    private static final UMessage MESSAGE = buildMessage(RESOURCE_URI, PAYLOAD, buildPublishAttributes());
    private static final UMessage NOTIFICATION_MESSAGE = buildMessage(RESOURCE_URI, PAYLOAD,
            newNotificationAttributesBuilder(CLIENT_URI).build());
    private static final CallOptions OPTIONS = CallOptions.newBuilder()
            .withTimeout(TTL)
            .build();
    private static final UPayload REQUEST_PAYLOAD = packToAny(Int32Value.newBuilder().setValue(1).build());
    private static final UPayload RESPONSE_PAYLOAD = packToAny(STATUS_OK);

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();
    private static final ServiceLifecycleListener sServiceLifecycleListener = mock(ServiceLifecycleListener.class);
    private static final UListener sListener = mock(UListener.class);
    private static final UListener sListener2 = mock(UListener.class);
    private static final URpcListener sRequestListener = mock(URpcListener.class);
    private static final URpcListener sRequestListener2 = mock(URpcListener.class);
    private static Context sContext;
    private static UPClient sClient;
    private static USubscription.Stub sSubscriptionStub;

    @BeforeClass
    public static void setUp() {
        sContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        sClient = UPClient.create(sContext, sExecutor, sServiceLifecycleListener);
        sSubscriptionStub = USubscription.newStub(sClient, OPTIONS);
        connect(sClient);
    }

    @AfterClass
    public static void tearDown() {
        unsubscribe(RESOURCE_URI);
        disconnect(sClient);
        sExecutor.shutdown();
    }

    @After
    public void tearDownTest() {
        reset(sServiceLifecycleListener);
        reset(sListener);
        reset(sListener2);
        reset(sRequestListener);
        reset(sRequestListener2);
        sClient.unregisterListener(sListener);
        sClient.unregisterListener(sListener2);
        sClient.unregisterRpcListener(sRequestListener);
        sClient.unregisterRpcListener(sRequestListener2);
    }

    private static void createTopic(@NonNull UUri topic) {
        CompletableFuture<UStatus> future = sSubscriptionStub.createTopic(CreateTopicRequest.newBuilder()
                        .setTopic(topic)
                        .build()).toCompletableFuture();
        assertStatus(UCode.OK, getOrThrow(future, OPTIONS.timeout()));
    }

    private static void subscribe(@NonNull UUri topic) {
        CompletableFuture<SubscriptionResponse> future = sSubscriptionStub.subscribe(SubscriptionRequest.newBuilder()
                .setTopic(topic)
                .setSubscriber(SubscriberInfo.newBuilder().
                        setUri(UUri.newBuilder()
                                .setEntity(sClient.getEntity())
                                .build()))
                .build()).toCompletableFuture();
        assertEquals(UCode.OK, getOrThrow(future, OPTIONS.timeout()).getStatus().getCode());
    }

    private static void unsubscribe(@NonNull UUri topic) {
        CompletableFuture<UStatus> future = sSubscriptionStub.unsubscribe(UnsubscribeRequest.newBuilder()
                .setTopic(topic)
                .setSubscriber(SubscriberInfo.newBuilder().
                        setUri(UUri.newBuilder()
                                .setEntity(sClient.getEntity())
                                .build()))
                .build()).toCompletableFuture();
        assertStatus(UCode.OK, getOrThrow(future, OPTIONS.timeout()));
    }

    @Test
    public void testConnect() {
        final UPClient client = UPClient.create(sContext, sExecutor, sServiceLifecycleListener);
        connect(client);
        verify(sServiceLifecycleListener, timeout(DELAY_MS).times(1)).onLifecycleChanged(client, true);
        client.disconnect();
    }

    @Test
    public void testConnectDuplicated() {
        final UPClient client = UPClient.create(sContext, sExecutor, sServiceLifecycleListener);
        final CompletableFuture<UStatus> future1 = client.connect().toCompletableFuture();
        final CompletableFuture<UStatus> future2 = client.connect().toCompletableFuture();
        assertStatus(UCode.OK, getOrThrow(future1, CONNECTION_TIMEOUT_MS));
        assertStatus(UCode.OK, getOrThrow(future2, CONNECTION_TIMEOUT_MS));
        verify(sServiceLifecycleListener, timeout(DELAY_MS).times(1)).onLifecycleChanged(client, true);
        assertTrue(client.isConnected());
        client.disconnect();
    }

    @Test
    public void testDisconnect() {
        final UPClient client = UPClient.create(sContext, sExecutor, sServiceLifecycleListener);
        connect(client);
        assertStatus(UCode.OK, getOrThrow(client.disconnect().toCompletableFuture(), DELAY_MS));
        verify(sServiceLifecycleListener, timeout(DELAY_MS).times(1)).onLifecycleChanged(client, false);
        assertTrue(client.isDisconnected());
    }

    @Test
    public void testDisconnectNotConnected() {
        final UPClient client = UPClient.create(sContext, sExecutor, sServiceLifecycleListener);
        assertStatus(UCode.OK, getOrThrow(client.disconnect().toCompletableFuture(), DELAY_MS));
        verify(sServiceLifecycleListener, timeout(DELAY_MS).times(0)).onLifecycleChanged(client, false);
        assertTrue(client.isDisconnected());
    }

    @Test
    public void testDisconnectWhileConnecting() {
        final UPClient client = UPClient.create(sContext, sExecutor, sServiceLifecycleListener);
        final CompletableFuture<UStatus> future = client.connect().toCompletableFuture();
        assertStatus(UCode.OK, getOrThrow(client.disconnect().toCompletableFuture(), DELAY_MS));
        assertTrue(Set.of(UCode.OK, UCode.CANCELLED)
                .contains(getOrThrow(future, CONNECTION_TIMEOUT_MS).getCode()));
        assertTrue(client.isDisconnected());
    }

    @Test
    public void testGetEntity() {
        assertEquals(CLIENT, sClient.getEntity());
    }

    @Test
    public void testSubscription() {
        createTopic(RESOURCE2_URI);
        subscribe(RESOURCE2_URI);
        unsubscribe(RESOURCE2_URI);
    }

    @Test
    public void testSend() {
        createTopic(RESOURCE_URI);
        assertStatus(UCode.OK, sClient.send(MESSAGE));
    }

    @Test
    public void testSendParts() {
        createTopic(RESOURCE_URI);
        assertStatus(UCode.OK, sClient.send(MESSAGE.getSource(), MESSAGE.getPayload(), MESSAGE.getAttributes()));
    }

    @Test
    public void testSendNotificationMassage() {
        assertStatus(UCode.OK, sClient.send(NOTIFICATION_MESSAGE));
    }

    @Test
    public void testRegisterListener() {
        assertStatus(UCode.OK, sClient.registerListener(RESOURCE_URI, sListener));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testRegisterListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, sClient.registerListener(UUri.getDefaultInstance(), sListener));
        assertStatus(UCode.INVALID_ARGUMENT, sClient.registerListener(RESOURCE_URI, null));
    }

    @Test
    public void testRegisterListenerDifferentTopics() {
        testRegisterListener();
        assertStatus(UCode.OK, sClient.registerListener(RESOURCE2_URI, sListener));
    }

    @Test
    public void testRegisterListenerSame() {
        testRegisterListener();
        assertStatus(UCode.OK, sClient.registerListener(RESOURCE_URI, sListener));
    }

    @Test
    public void testRegisterListenerNotFirst() {
        testRegisterListener();
        assertStatus(UCode.OK, sClient.registerListener(RESOURCE_URI, sListener2));
    }

    @Test
    public void testUnregisterListener() {
        testRegisterListener();
        assertStatus(UCode.OK, sClient.unregisterListener(RESOURCE_URI, sListener));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, sClient.unregisterListener(UUri.getDefaultInstance(), sListener));
        assertStatus(UCode.INVALID_ARGUMENT, sClient.unregisterListener(RESOURCE_URI, null));
    }

    @Test
    public void testUnregisterListenerSame() {
        testUnregisterListener();
        assertStatus(UCode.OK, sClient.unregisterListener(RESOURCE_URI, sListener));
    }

    @Test
    public void testUnregisterListenerNotRegistered() {
        testRegisterListener();
        assertStatus(UCode.OK, sClient.unregisterListener(RESOURCE_URI, sListener2));
    }

    @Test
    public void testUnregisterListenerNotLast() {
        testRegisterListenerNotFirst();
        assertStatus(UCode.OK, sClient.unregisterListener(RESOURCE_URI, sListener));
    }

    @Test
    public void testUnregisterListenerLast() {
        testUnregisterListenerNotLast();
        assertStatus(UCode.OK, sClient.unregisterListener(RESOURCE_URI, sListener2));
    }

    @Test
    public void testUnregisterListenerFromAllTopics() {
        testRegisterListenerDifferentTopics();
        assertStatus(UCode.OK, sClient.unregisterListener(sListener));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterListenerFromAllTopicsWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, sClient.unregisterListener(null));
    }

    @Test
    public void testOnReceiveGenericMessage() {
        testSend();
        subscribe(RESOURCE_URI);
        testRegisterListenerNotFirst();
        verify(sListener, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
        verify(sListener2, timeout(DELAY_MS).atLeastOnce()).onReceive(MESSAGE);
    }

    @Test
    public void testOnReceiveGenericMessageNotRegistered() {
        testSend();
        subscribe(RESOURCE_URI);
        testRegisterListener();
        verify(sListener, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
        verify(sListener2, timeout(DELAY_MS).times(0)).onReceive(MESSAGE);
    }

    @Test
    public void testOnReceiveNotificationMessage() {
        testRegisterListener();
        testSendNotificationMassage();
        verify(sListener, timeout(DELAY_MS).times(1)).onReceive(NOTIFICATION_MESSAGE);
    }

    @Test
    public void testRegisterRpcListener() {
        assertEquals(STATUS_OK, sClient.registerRpcListener(METHOD_URI, sRequestListener));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testRegisterRpcListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, sClient.registerRpcListener(UUri.getDefaultInstance(), sRequestListener));
        assertStatus(UCode.INVALID_ARGUMENT, sClient.registerRpcListener(METHOD_URI, null));
    }

    @Test
    public void testRegisterRpcListenerDifferentMethods() {
        assertStatus(UCode.OK, sClient.registerRpcListener(METHOD_URI, sRequestListener));
        assertStatus(UCode.OK, sClient.registerRpcListener(METHOD2_URI, sRequestListener));
    }

    @Test
    public void testRegisterRpcListenerSame() {
        testRegisterRpcListener();
        assertStatus(UCode.OK, sClient.registerRpcListener(METHOD_URI, sRequestListener));
    }

    @Test
    public void testRegisterRpcListenerNotFirst() {
        testRegisterRpcListener();
        assertStatus(UCode.ALREADY_EXISTS, sClient.registerRpcListener(METHOD_URI, sRequestListener2));
    }

    @Test
    public void testUnregisterRpcListener() {
        testRegisterRpcListener();
        assertStatus(UCode.OK, sClient.unregisterRpcListener(METHOD_URI, sRequestListener));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterRpcListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, sClient.unregisterRpcListener(UUri.getDefaultInstance(), sRequestListener));
        assertStatus(UCode.INVALID_ARGUMENT, sClient.unregisterRpcListener(METHOD_URI, null));
    }

    @Test
    public void testUnregisterRpcListenerSame() {
        testUnregisterRpcListener();
        assertStatus(UCode.OK, sClient.unregisterRpcListener(METHOD_URI, sRequestListener));
    }

    @Test
    public void testUnregisterRpcListenerNotRegistered() {
        testRegisterRpcListener();
        assertStatus(UCode.OK, sClient.unregisterRpcListener(METHOD_URI, sRequestListener2));
    }

    @Test
    public void testUnregisterRpcListenerFromAllMethods() {
        testRegisterRpcListenerDifferentMethods();
        assertStatus(UCode.OK, sClient.unregisterRpcListener(sRequestListener));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterRpcListenerFromAllMethodsWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, sClient.unregisterRpcListener(null));
    }

    @Test
    public void testUnregisterRpcListenerFromAllMethodsNotRegistered() {
        testRegisterRpcListenerDifferentMethods();
        assertStatus(UCode.OK, sClient.unregisterRpcListener(sRequestListener2));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInvokeMethod() throws Exception {
        testRegisterRpcListener();

        final CompletableFuture<UMessage> responseFuture =
                sClient.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, OPTIONS).toCompletableFuture();
        assertFalse(responseFuture.isDone());

        final ArgumentCaptor<UMessage> requestCaptor = ArgumentCaptor.forClass(UMessage.class);
        final ArgumentCaptor<CompletableFuture<UPayload>> responseFutureCaptor =
                ArgumentCaptor.forClass(CompletableFuture.class);
        verify(sRequestListener, timeout(DELAY_MS).times(1))
                .onReceive(requestCaptor.capture(), responseFutureCaptor.capture());
        final UMessage requestMessage = requestCaptor.getValue();
        assertEquals(RESPONSE_URI.getEntity(), requestMessage.getSource().getEntity());
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
                () -> sClient.invokeMethod(null, PAYLOAD, OPTIONS).toCompletableFuture().get())));
        assertStatus(UCode.INVALID_ARGUMENT, toStatus(assertThrows(ExecutionException.class,
                () -> sClient.invokeMethod(UUri.getDefaultInstance(), PAYLOAD, OPTIONS).toCompletableFuture().get())));
        assertStatus(UCode.INVALID_ARGUMENT, toStatus(assertThrows(ExecutionException.class,
                () -> sClient.invokeMethod(METHOD_URI, null, OPTIONS).toCompletableFuture().get())));
        assertStatus(UCode.INVALID_ARGUMENT, toStatus(assertThrows(ExecutionException.class,
                () -> sClient.invokeMethod(METHOD_URI, PAYLOAD, null).toCompletableFuture().get())));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInvokeMethodCompletedWithCommStatus() {
        testRegisterRpcListener();

        final CompletableFuture<UMessage> responseFuture =
                sClient.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, OPTIONS).toCompletableFuture();
        assertFalse(responseFuture.isDone());

        final ArgumentCaptor<CompletableFuture<UPayload>> responseFutureCaptor =
                ArgumentCaptor.forClass(CompletableFuture.class);
        verify(sRequestListener, timeout(DELAY_MS).times(1)).onReceive(any(), responseFutureCaptor.capture());
        responseFutureCaptor.getValue().completeExceptionally(new UStatusException(UCode.CANCELLED, "Cancelled"));
        assertStatus(UCode.CANCELLED, toStatus(assertThrows(
                ExecutionException.class, () -> responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS))));
    }

    @Test
    public void testInvokeMethodNoServer() {
        assertStatus(UCode.UNAVAILABLE, toStatus(assertThrows(ExecutionException.class,
                () -> sClient.invokeMethod(METHOD_URI, PAYLOAD, OPTIONS).toCompletableFuture().get())));
    }
}
