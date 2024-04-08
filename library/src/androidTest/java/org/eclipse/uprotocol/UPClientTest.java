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
import org.eclipse.uprotocol.core.usubscription.v3.CreateTopicRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionResponse;
import org.eclipse.uprotocol.core.usubscription.v3.USubscription;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeRequest;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.transport.builder.UAttributesBuilder;
import org.eclipse.uprotocol.v1.CallOptions;
import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UMessageType;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UPriority;
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
    private static final UMessage MESSAGE = buildMessage(PAYLOAD, buildPublishAttributes(RESOURCE_URI));
    private static final UMessage NOTIFICATION_MESSAGE = buildMessage(PAYLOAD,
            newNotificationAttributesBuilder(RESOURCE_URI, CLIENT_URI).build());
    private static final CallOptions OPTIONS = CallOptions.newBuilder()
            .setPriority(UPriority.UPRIORITY_CS4)
            .setTtl(TTL)
            .build();
    private static final UPayload REQUEST_PAYLOAD = packToAny(Int32Value.newBuilder().setValue(1).build());
    private static final UPayload RESPONSE_PAYLOAD = packToAny(STATUS_OK);

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();
    private static final ServiceLifecycleListener sServiceLifecycleListener = mock(ServiceLifecycleListener.class);
    private static final UListener sListener = mock(UListener.class);
    private static final UListener sListener2 = mock(UListener.class);
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
        sClient.unregisterListener(sListener);
        sClient.unregisterListener(sListener2);
    }

    private static void createTopic(@NonNull UUri topic) {
        CompletableFuture<UStatus> future = sSubscriptionStub.createTopic(CreateTopicRequest.newBuilder()
                        .setTopic(topic)
                        .build()).toCompletableFuture();
        assertStatus(UCode.OK, getOrThrow(future, OPTIONS.getTtl()));
    }

    private static void subscribe(@NonNull UUri topic) {
        CompletableFuture<SubscriptionResponse> future = sSubscriptionStub.subscribe(SubscriptionRequest.newBuilder()
                .setTopic(topic)
                .setSubscriber(SubscriberInfo.newBuilder().
                        setUri(UUri.newBuilder()
                                .setEntity(sClient.getEntity())
                                .build()))
                .build()).toCompletableFuture();
        assertEquals(UCode.OK, getOrThrow(future, OPTIONS.getTtl()).getStatus().getCode());
    }

    private static void unsubscribe(@NonNull UUri topic) {
        CompletableFuture<UStatus> future = sSubscriptionStub.unsubscribe(UnsubscribeRequest.newBuilder()
                .setTopic(topic)
                .setSubscriber(SubscriberInfo.newBuilder().
                        setUri(UUri.newBuilder()
                                .setEntity(sClient.getEntity())
                                .build()))
                .build()).toCompletableFuture();
        assertStatus(UCode.OK, getOrThrow(future, OPTIONS.getTtl()));
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
    public void testSendNotificationMessage() {
        assertStatus(UCode.OK, sClient.send(NOTIFICATION_MESSAGE));
    }

    @Test
    public void testRegisterGenericListener() {
        assertStatus(UCode.OK, sClient.registerListener(RESOURCE_URI, sListener));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testRegisterGenericListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, sClient.registerListener(UUri.getDefaultInstance(), sListener));
        assertStatus(UCode.INVALID_ARGUMENT, sClient.registerListener(RESOURCE_URI, null));
    }

    @Test
    public void testRegisterGenericListenerDifferentTopics() {
        testRegisterGenericListener();
        assertStatus(UCode.OK, sClient.registerListener(RESOURCE2_URI, sListener));
    }

    @Test
    public void testRegisterGenericListenerSame() {
        testRegisterGenericListener();
        assertStatus(UCode.OK, sClient.registerListener(RESOURCE_URI, sListener));
    }

    @Test
    public void testRegisterGenericListenerNotFirst() {
        testRegisterGenericListener();
        assertStatus(UCode.OK, sClient.registerListener(RESOURCE_URI, sListener2));
    }

    @Test
    public void testUnregisterGenericListener() {
        testRegisterGenericListener();
        assertStatus(UCode.OK, sClient.unregisterListener(RESOURCE_URI, sListener));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterGenericListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, sClient.unregisterListener(UUri.getDefaultInstance(), sListener));
        assertStatus(UCode.INVALID_ARGUMENT, sClient.unregisterListener(RESOURCE_URI, null));
    }

    @Test
    public void testUnregisterGenericListenerSame() {
        testUnregisterGenericListener();
        assertStatus(UCode.OK, sClient.unregisterListener(RESOURCE_URI, sListener));
    }

    @Test
    public void testUnregisterGenericListenerNotRegistered() {
        testRegisterGenericListener();
        assertStatus(UCode.OK, sClient.unregisterListener(RESOURCE_URI, sListener2));
    }

    @Test
    public void testUnregisterGenericListenerNotLast() {
        testRegisterGenericListenerNotFirst();
        assertStatus(UCode.OK, sClient.unregisterListener(RESOURCE_URI, sListener));
    }

    @Test
    public void testUnregisterGenericListenerLast() {
        testUnregisterGenericListenerNotLast();
        assertStatus(UCode.OK, sClient.unregisterListener(RESOURCE_URI, sListener2));
    }

    @Test
    public void testUnregisterGenericListenerFromAllTopics() {
        testRegisterGenericListenerDifferentTopics();
        assertStatus(UCode.OK, sClient.unregisterListener(sListener));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterGenericListenerFromAllTopicsWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, sClient.unregisterListener(null));
    }

    @Test
    public void testOnReceiveGenericMessage() {
        testSend();
        subscribe(RESOURCE_URI);
        testRegisterGenericListenerNotFirst();
        verify(sListener, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
        verify(sListener2, timeout(DELAY_MS).atLeastOnce()).onReceive(MESSAGE);
    }

    @Test
    public void testOnReceiveGenericMessageNotRegistered() {
        testSend();
        subscribe(RESOURCE_URI);
        testRegisterGenericListener();
        verify(sListener, timeout(DELAY_MS).times(1)).onReceive(MESSAGE);
        verify(sListener2, timeout(DELAY_MS).times(0)).onReceive(MESSAGE);
    }

    @Test
    public void testOnReceiveNotificationMessage() {
        testRegisterGenericListener();
        testSendNotificationMessage();
        verify(sListener, timeout(DELAY_MS).times(1)).onReceive(NOTIFICATION_MESSAGE);
    }

    @Test
    public void testRegisterRequestListener() {
        assertEquals(STATUS_OK, sClient.registerListener(METHOD_URI, sListener));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testRegisterRequestListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, sClient.registerListener(UUri.getDefaultInstance(), sListener));
        assertStatus(UCode.INVALID_ARGUMENT, sClient.registerListener(METHOD_URI, null));
    }

    @Test
    public void testRegisterRequestListenerDifferentMethods() {
        assertStatus(UCode.OK, sClient.registerListener(METHOD_URI, sListener));
        assertStatus(UCode.OK, sClient.registerListener(METHOD2_URI, sListener));
    }

    @Test
    public void testRegisterRequestListenerSame() {
        testRegisterRequestListener();
        assertStatus(UCode.OK, sClient.registerListener(METHOD_URI, sListener));
    }

    @Test
    public void testRegisterRequestListenerNotFirst() {
        testRegisterRequestListener();
        assertStatus(UCode.ALREADY_EXISTS, sClient.registerListener(METHOD_URI, sListener2));
    }

    @Test
    public void testUnregisterRequestListener() {
        testRegisterRequestListener();
        assertStatus(UCode.OK, sClient.unregisterListener(METHOD_URI, sListener));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterRequestListenerWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, sClient.unregisterListener(UUri.getDefaultInstance(), sListener));
        assertStatus(UCode.INVALID_ARGUMENT, sClient.unregisterListener(METHOD_URI, null));
    }

    @Test
    public void testUnregisterRequestListenerSame() {
        testUnregisterRequestListener();
        assertStatus(UCode.OK, sClient.unregisterListener(METHOD_URI, sListener));
    }

    @Test
    public void testUnregisterRequestListenerNotRegistered() {
        testRegisterRequestListener();
        assertStatus(UCode.OK, sClient.unregisterListener(METHOD_URI, sListener2));
    }

    @Test
    public void testUnregisterRequestListenerFromAllMethods() {
        testRegisterRequestListenerDifferentMethods();
        assertStatus(UCode.OK, sClient.unregisterListener(sListener));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterRequestListenerFromAllMethodsWithInvalidArgument() {
        assertStatus(UCode.INVALID_ARGUMENT, sClient.unregisterListener(null));
    }

    @Test
    public void testUnregisterRequestListenerFromAllMethodsNotRegistered() {
        testRegisterRequestListenerDifferentMethods();
        assertStatus(UCode.OK, sClient.unregisterListener(sListener2));
    }

    @Test
    public void testInvokeMethod() throws Exception {
        testRegisterRequestListener();

        final CompletableFuture<UMessage> responseFuture =
                sClient.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, OPTIONS).toCompletableFuture();
        assertFalse(responseFuture.isDone());

        final ArgumentCaptor<UMessage> requestCaptor = ArgumentCaptor.forClass(UMessage.class);
        verify(sListener, timeout(DELAY_MS).times(1)).onReceive(requestCaptor.capture());
        final UMessage requestMessage = requestCaptor.getValue();
        final UAttributes requestAttributes = requestMessage.getAttributes();
        assertEquals(RESPONSE_URI.getEntity(), requestAttributes.getSource().getEntity());
        assertEquals(REQUEST_PAYLOAD, requestMessage.getPayload());
        assertEquals(METHOD_URI, requestAttributes.getSink());
        assertEquals(OPTIONS.getPriority(), requestAttributes.getPriority());
        assertEquals(OPTIONS.getTtl(), requestAttributes.getTtl());
        assertEquals(UMessageType.UMESSAGE_TYPE_REQUEST, requestAttributes.getType());
        sClient.send(UMessage.newBuilder()
                .setPayload(RESPONSE_PAYLOAD)
                .setAttributes(UAttributesBuilder.response(requestAttributes).build())
                .build());

        final UMessage responseMessage = responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS);
        final UAttributes responseAttributes = responseMessage.getAttributes();;
        assertEquals(METHOD_URI, responseAttributes.getSource());
        assertEquals(RESPONSE_PAYLOAD, responseMessage.getPayload());
        assertEquals(RESPONSE_URI, responseAttributes.getSink());
        assertEquals(UMessageType.UMESSAGE_TYPE_RESPONSE, responseAttributes.getType());
        assertEquals(requestAttributes.getId(), responseAttributes.getReqid());
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
    public void testInvokeMethodCompletedWithCommStatus() {
        testRegisterRequestListener();

        final CompletableFuture<UMessage> responseFuture =
                sClient.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, OPTIONS).toCompletableFuture();
        assertFalse(responseFuture.isDone());

        final ArgumentCaptor<UMessage> requestCaptor = ArgumentCaptor.forClass(UMessage.class);
        verify(sListener, timeout(DELAY_MS).times(1)).onReceive(requestCaptor.capture());
        final UMessage requestMessage = requestCaptor.getValue();
        final UAttributes requestAttributes = requestMessage.getAttributes();
        sClient.send(UMessage.newBuilder()
                .setAttributes(UAttributesBuilder
                        .response(requestAttributes)
                        .withCommStatus(UCode.CANCELLED)
                        .build())
                .build());

        assertStatus(UCode.CANCELLED, toStatus(assertThrows(
                ExecutionException.class, () -> responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS))));
    }

    @Test
    public void testInvokeMethodNoServer() {
        assertStatus(UCode.UNAVAILABLE, toStatus(assertThrows(ExecutionException.class,
                () -> sClient.invokeMethod(METHOD_URI, PAYLOAD, OPTIONS).toCompletableFuture().get())));
    }
}
