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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.eclipse.uprotocol.client.usubscription.v3.InMemoryUSubscriptionClient;
import org.eclipse.uprotocol.client.usubscription.v3.USubscriptionClient;
import org.eclipse.uprotocol.communication.CallOptions;
import org.eclipse.uprotocol.communication.RequestHandler;
import org.eclipse.uprotocol.communication.UClient;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.transport.UTransportAndroid;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UStatus;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class UTransportAndroidTest extends TestBase {
    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();
    private static final UListener sListener = mock(UListener.class);
    private static final RequestHandler sRequestHandler = mock(RequestHandler.class);
    private static Context sContext;
    private static UTransportAndroid sTransport;
    private static UClient sClient;
    private static USubscriptionClient sSubscriptionClient;

    @BeforeClass
    public static void setUp() {
        sContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        sTransport = UTransportAndroid.create(sContext, sExecutor);
        assertStatus(UCode.OK, getOrThrow(sTransport.open(), CONNECTION_TIMEOUT_MS));
        sClient = UClient.create(sTransport);
        sSubscriptionClient = new InMemoryUSubscriptionClient(sTransport);
    }

    @AfterClass
    public static void tearDown() {
        sTransport.close();
        sExecutor.shutdown();
    }

    @After
    public void tearDownTest() {
        reset(sListener);
        reset(sRequestHandler);
    }

    @Test
    public void testOpen() {
        final UTransportAndroid transport = UTransportAndroid.create(sContext, sExecutor);
        assertStatus(UCode.OK, getOrThrow(transport.open(), CONNECTION_TIMEOUT_MS));
        assertTrue(transport.isOpened());
        transport.close();
    }

    @Test
    public void testOpenDuplicated() {
        final UTransportAndroid transport = UTransportAndroid.create(sContext, sExecutor);
        final CompletionStage<UStatus> stage1 = transport.open();
        final CompletionStage<UStatus> stage2 = transport.open();
        assertStatus(UCode.OK, getOrThrow(stage1, CONNECTION_TIMEOUT_MS));
        assertStatus(UCode.OK, getOrThrow(stage2, CONNECTION_TIMEOUT_MS));
        assertTrue(transport.isOpened());
        transport.close();
    }

    @Test
    public void testClose() {
        final UTransportAndroid transport = UTransportAndroid.create(sContext, sExecutor);
        assertStatus(UCode.OK, getOrThrow(transport.open(), CONNECTION_TIMEOUT_MS));
        transport.close();
        assertFalse(transport.isOpened());
    }

    @Test
    public void testCloseNotOpened() {
        final UTransportAndroid transport = UTransportAndroid.create(sContext, sExecutor);
        transport.close();
        assertFalse(transport.isOpened());
    }

    @Test
    public void testCloseWhileOpening() {
        final UTransportAndroid transport = UTransportAndroid.create(sContext, sExecutor);
        final CompletionStage<UStatus> stage = transport.open();
        transport.close();
        assertTrue(Set.of(UCode.OK, UCode.CANCELLED).contains(getOrThrow(stage, CONNECTION_TIMEOUT_MS).getCode()));
        assertFalse(transport.isOpened());
    }

    @Test
    public void testGetSource() {
        assertEquals(CLIENT_URI, sTransport.getSource());
    }

    @Test
    public void testSendPublishMessage() {
        assertStatus(UCode.OK, getOrThrow(sClient.publish(RESOURCE_URI, PAYLOAD)));
    }

    @Test
    public void testSendNotificationMessage() {
        assertStatus(UCode.OK, getOrThrow(sClient.notify(RESOURCE_URI, CLIENT_URI, PAYLOAD)));
    }

    @Test
    public void testOnReceivePublishMessage() {
        assertEquals(State.SUBSCRIBED,
                getOrThrow(sSubscriptionClient.subscribe(RESOURCE_URI, sListener), TTL).getStatus().getState());
        testSendPublishMessage();
        verify(sListener, timeout(DELAY_MS).times(1)).onReceive(argThat(message -> {
            assertEquals(RESOURCE_URI, message.getAttributes().getSource());
            assertEquals(PAYLOAD.data(), message.getPayload());
            return true;
        }));
        assertStatus(UCode.OK, getOrThrow(sSubscriptionClient.unsubscribe(RESOURCE_URI, sListener), TTL));
    }

    @Test
    public void testOnReceiveNotificationMessage() {
        assertStatus(UCode.OK, getOrThrow(sClient.registerNotificationListener(RESOURCE_URI, sListener)));
        testSendNotificationMessage();
        verify(sListener, timeout(DELAY_MS).times(1)).onReceive(argThat(message -> {
            assertEquals(RESOURCE_URI, message.getAttributes().getSource());
            assertEquals(CLIENT_URI, message.getAttributes().getSink());
            assertEquals(PAYLOAD.data(), message.getPayload());
            return true;
        }));
        assertStatus(UCode.OK, getOrThrow(sClient.unregisterNotificationListener(RESOURCE_URI, sListener)));
    }

    @Test
    public void testOnReceiveRpcMessages() {
        sClient.registerRequestHandler(METHOD_URI, sRequestHandler);
        doReturn(PAYLOAD2).when(sRequestHandler).handleRequest(argThat(message -> {
            assertEquals(CLIENT_URI, message.getAttributes().getSource());
            assertEquals(METHOD_URI, message.getAttributes().getSink());
            assertEquals(PAYLOAD.data(), message.getPayload());
            return true;
        }));
        assertEquals(PAYLOAD2, getOrThrow(sClient.invokeMethod(METHOD_URI, PAYLOAD, CallOptions.DEFAULT), TTL));
        sClient.unregisterRequestHandler(METHOD_URI, sRequestHandler);
    }
}
