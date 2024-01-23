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
package org.eclipse.uprotocol.core.udiscovery.v3;

import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
import org.eclipse.uprotocol.uri.builder.UResourceBuilder;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;

@RunWith(AndroidJUnit4.class)
public class UDiscoveryTest extends TestBase {
    private RpcClient mClient;
    private UDiscovery.Stub mStub;

    @Before
    public void setUp() throws RemoteException {
        mClient = mock(RpcClient.class);
        mStub = UDiscovery.newStub(mClient);
    }

    private void simulateResponse(@NonNull Message message) {
        doReturn(CompletableFuture.completedFuture(packToAny(message))).when(mClient).invokeMethod(any(), any(), any());
    }

    @Test
    public void testEntity() {
        final ServiceOptions options = UDiscoveryProto.getDescriptor().findServiceByName("uDiscovery").getOptions();
        assertEquals(options.getExtension(UprotocolOptions.name), UDiscovery.SERVICE.getName());
        assertEquals((int) options.getExtension(UprotocolOptions.versionMajor), UDiscovery.SERVICE.getVersionMajor());
    }

    @Test
    public void testNewStub() {
        assertFalse(mStub.getAuthority().isPresent());
        assertEquals(CallOptions.DEFAULT, mStub.getOptions());
    }

    @Test
    public void testNewStubWithCallOptions() {
        mStub = UDiscovery.newStub(mClient, OPTIONS);
        assertFalse(mStub.getAuthority().isPresent());
        assertEquals(OPTIONS, mStub.getOptions());
    }

    @Test
    public void testNewStubWithAuthorityAndCallOptions() {
        mStub = UDiscovery.newStub(mClient, AUTHORITY_REMOTE, OPTIONS);
        assertEquals(AUTHORITY_REMOTE, mStub.getAuthority().orElse(null));
        assertEquals(OPTIONS, mStub.getOptions());
    }

    @Test
    public void testLookupUri() {
        final UUri request = UUri.getDefaultInstance();
        final LookupUriResponse response = LookupUriResponse.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.lookupUri(request).toCompletableFuture()));
    }

    @Test
    public void testUpdateNode() {
        final UpdateNodeRequest request = UpdateNodeRequest.getDefaultInstance();
        final UStatus response = UStatus.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.updateNode(request).toCompletableFuture()));
    }

    @Test
    public void testFindNodes() {
        final FindNodesRequest request = FindNodesRequest.getDefaultInstance();
        final FindNodesResponse response = FindNodesResponse.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.findNodes(request).toCompletableFuture()));
    }

    @Test
    public void testFindNodeProperties() {
        final FindNodePropertiesRequest request = FindNodePropertiesRequest.getDefaultInstance();
        final FindNodePropertiesResponse response = FindNodePropertiesResponse.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.findNodeProperties(request).toCompletableFuture()));
    }

    @Test
    public void testDeleteNodes() {
        final DeleteNodesRequest request = DeleteNodesRequest.getDefaultInstance();
        final UStatus response = UStatus.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.deleteNodes(request).toCompletableFuture()));
    }

    @Test
    public void testAddNodes() {
        final AddNodesRequest request = AddNodesRequest.getDefaultInstance();
        final UStatus response = UStatus.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.addNodes(request).toCompletableFuture()));
    }

    @Test
    public void testUnregisterForNotifications() {
        final NotificationsRequest request = NotificationsRequest.getDefaultInstance();
        final UStatus response = UStatus.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.unregisterForNotifications(request).toCompletableFuture()));
    }

    @Test
    public void testResolveUri() {
        final ResolveUriRequest request = ResolveUriRequest.getDefaultInstance();
        final ResolveUriResponse response = ResolveUriResponse.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.resolveUri(request).toCompletableFuture()));
    }

    @Test
    public void testRegisterForNotifications() {
        final NotificationsRequest request = NotificationsRequest.getDefaultInstance();
        final UStatus response = UStatus.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.registerForNotifications(request).toCompletableFuture()));
    }

    @Test
    public void testUpdateProperty() {
        final UpdatePropertyRequest request = UpdatePropertyRequest.getDefaultInstance();
        final UStatus response = UStatus.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.updateProperty(request).toCompletableFuture()));
    }

    @Test
    public void tesCallWithAuthority() {
        testNewStubWithAuthorityAndCallOptions();
        doReturn(new CompletableFuture<>()).when(mClient).invokeMethod(argThat(uri -> {
            assertEquals(AUTHORITY_REMOTE, uri.getAuthority());
            assertEquals(UDiscovery.SERVICE, uri.getEntity());
            assertEquals(UResourceBuilder.forRpcRequest(UDiscovery.METHOD_LOOKUP_URI), uri.getResource());
            return true;
        }), any(), any());
        assertFalse(mStub.lookupUri(UUri.getDefaultInstance()).toCompletableFuture().isDone());
    }
}
