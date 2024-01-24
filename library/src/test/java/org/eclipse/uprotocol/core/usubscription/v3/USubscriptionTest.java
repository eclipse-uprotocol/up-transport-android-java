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
package org.eclipse.uprotocol.core.usubscription.v3;

import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.protobuf.DescriptorProtos.ServiceOptions;
import com.google.protobuf.Message;

import org.eclipse.uprotocol.TestBase;
import org.eclipse.uprotocol.UprotocolOptions;
import org.eclipse.uprotocol.rpc.CallOptions;
import org.eclipse.uprotocol.rpc.RpcClient;
import org.eclipse.uprotocol.uri.factory.UResourceBuilder;
import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;

@RunWith(AndroidJUnit4.class)
public class USubscriptionTest extends TestBase {
    private RpcClient mClient;
    private USubscription.Stub mStub;

    @Before
    public void setUp() throws RemoteException {
        mClient = mock(RpcClient.class);
        mStub = USubscription.newStub(mClient);
    }

    private void simulateResponse(@NonNull Message response) {
        doAnswer(invocation -> {
            final UUri methodUri = invocation.getArgument(0);
            final UAttributes responseAttributes = buildResponseAttributes(RESPONSE_URI, ID);
            return CompletableFuture.completedFuture(buildMessage(methodUri, packToAny(response), responseAttributes));
        }).when(mClient).invokeMethod(any(), any(), any());
    }

    @Test
    public void testEntity() {
        final ServiceOptions options = USubscriptionProto.getDescriptor().findServiceByName("uSubscription").getOptions();
        assertEquals(options.getExtension(UprotocolOptions.name), USubscription.SERVICE.getName());
        assertEquals((int) options.getExtension(UprotocolOptions.versionMajor), USubscription.SERVICE.getVersionMajor());
    }

    @Test
    public void testNewStub() {
        assertFalse(mStub.getAuthority().isPresent());
        assertEquals(CallOptions.DEFAULT, mStub.getOptions());
    }

    @Test
    public void testNewStubWithCallOptions() {
        mStub = USubscription.newStub(mClient, OPTIONS);
        assertFalse(mStub.getAuthority().isPresent());
        assertEquals(OPTIONS, mStub.getOptions());
    }

    @Test
    public void testNewStubWithAuthorityAndCallOptions() {
        mStub = USubscription.newStub(mClient, AUTHORITY_REMOTE, OPTIONS);
        assertEquals(AUTHORITY_REMOTE, mStub.getAuthority().orElse(null));
        assertEquals(OPTIONS, mStub.getOptions());
    }

    @Test
    public void testSubscribe() {
        final SubscriptionRequest request = SubscriptionRequest.getDefaultInstance();
        final SubscriptionResponse response = SubscriptionResponse.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.subscribe(request).toCompletableFuture()));
    }

    @Test
    public void testUnsubscribe() {
        final UnsubscribeRequest request = UnsubscribeRequest.getDefaultInstance();
        final UStatus response = UStatus.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.unsubscribe(request).toCompletableFuture()));
    }

    @Test
    public void testFetchSubscriptions() {
        final FetchSubscriptionsRequest request = FetchSubscriptionsRequest.getDefaultInstance();
        final FetchSubscriptionsResponse response = FetchSubscriptionsResponse.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.fetchSubscriptions(request).toCompletableFuture()));
    }

    @Test
    public void testCreateTopic() {
        final CreateTopicRequest request = CreateTopicRequest.getDefaultInstance();
        final UStatus response = UStatus.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.createTopic(request).toCompletableFuture()));
    }

    @Test
    public void testDeprecateTopic() {
        final DeprecateTopicRequest request = DeprecateTopicRequest.getDefaultInstance();
        final UStatus response = UStatus.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.deprecateTopic(request).toCompletableFuture()));
    }

    @Test
    public void testRegisterForNotifications() {
        final NotificationsRequest request = NotificationsRequest.getDefaultInstance();
        final UStatus response = UStatus.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.registerForNotifications(request).toCompletableFuture()));
    }

    @Test
    public void testUnregisterForNotifications() {
        final NotificationsRequest request = NotificationsRequest.getDefaultInstance();
        final UStatus response = UStatus.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.unregisterForNotifications(request).toCompletableFuture()));
    }

    @Test
    public void testFetchSubscribers() {
        final FetchSubscribersRequest request = FetchSubscribersRequest.getDefaultInstance();
        final FetchSubscribersResponse response = FetchSubscribersResponse.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.fetchSubscribers(request).toCompletableFuture()));
    }

    @Test
    public void testReset() {
        final ResetRequest request = ResetRequest.getDefaultInstance();
        final UStatus response = UStatus.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.reset(request).toCompletableFuture()));
    }

    @Test
    public void tesCallWithAuthority() {
        testNewStubWithAuthorityAndCallOptions();
        doReturn(new CompletableFuture<>()).when(mClient).invokeMethod(argThat(uri -> {
            assertEquals(AUTHORITY_REMOTE, uri.getAuthority());
            assertEquals(USubscription.SERVICE, uri.getEntity());
            assertEquals(UResourceBuilder.forRpcRequest(USubscription.METHOD_SUBSCRIBE), uri.getResource());
            return true;
        }), any(), any());
        assertFalse(mStub.subscribe(SubscriptionRequest.getDefaultInstance()).toCompletableFuture().isDone());
    }
}
