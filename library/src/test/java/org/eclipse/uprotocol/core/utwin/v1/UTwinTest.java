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
package org.eclipse.uprotocol.core.utwin.v1;

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
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUriBatch;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;

@RunWith(AndroidJUnit4.class)
public class UTwinTest extends TestBase {
    private RpcClient mClient;
    private UTwin.Stub mStub;

    @Before
    public void setUp() throws RemoteException {
        mClient = mock(RpcClient.class);
        mStub = UTwin.newStub(mClient);
    }

    private void simulateResponse(@NonNull Message message) {
        doReturn(CompletableFuture.completedFuture(packToAny(message))).when(mClient).invokeMethod(any(), any(), any());
    }

    @Test
    public void testEntity() {
        final ServiceOptions options = UTwinProto.getDescriptor().findServiceByName("uTwin").getOptions();
        assertEquals(options.getExtension(UprotocolOptions.name), UTwin.SERVICE.getName());
        assertEquals((int) options.getExtension(UprotocolOptions.versionMajor), UTwin.SERVICE.getVersionMajor());
    }

    @Test
    public void testNewStub() {
        assertFalse(mStub.getAuthority().isPresent());
        assertEquals(CallOptions.DEFAULT, mStub.getOptions());
    }

    @Test
    public void testNewStubWithCallOptions() {
        mStub = UTwin.newStub(mClient, OPTIONS);
        assertFalse(mStub.getAuthority().isPresent());
        assertEquals(OPTIONS, mStub.getOptions());
    }

    @Test
    public void testNewStubWithAuthorityAndCallOptions() {
        mStub = UTwin.newStub(mClient, AUTHORITY_REMOTE, OPTIONS);
        assertEquals(AUTHORITY_REMOTE, mStub.getAuthority().orElse(null));
        assertEquals(OPTIONS, mStub.getOptions());
    }

    @Test
    public void testGetLastMessages() {
        final UUriBatch request = UUriBatch.getDefaultInstance();
        final GetLastMessagesResponse response = GetLastMessagesResponse.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.getLastMessages(request).toCompletableFuture()));
    }

    @Test
    public void testSetLastMessage() {
        final UMessage request = UMessage.getDefaultInstance();
        final UStatus response = UStatus.getDefaultInstance();
        simulateResponse(response);
        assertEquals(response, getOrThrow(mStub.setLastMessage(request).toCompletableFuture()));
    }

    @Test
    public void tesCallWithAuthority() {
        testNewStubWithAuthorityAndCallOptions();
        doReturn(new CompletableFuture<>()).when(mClient).invokeMethod(argThat(uri -> {
            assertEquals(AUTHORITY_REMOTE, uri.getAuthority());
            assertEquals(UTwin.SERVICE, uri.getEntity());
            assertEquals(UResourceBuilder.forRpcRequest(UTwin.METHOD_SET_LAST_MESSAGE), uri.getResource());
            return true;
        }), any(), any());
        assertFalse(mStub.setLastMessage(UMessage.getDefaultInstance()).toCompletableFuture().isDone());
    }
}
