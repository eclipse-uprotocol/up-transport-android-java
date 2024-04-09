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

import static org.eclipse.uprotocol.rpc.RpcMapper.mapResponse;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;

import org.eclipse.uprotocol.rpc.RpcClient;
import org.eclipse.uprotocol.uri.factory.UResourceBuilder;
import org.eclipse.uprotocol.v1.CallOptions;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UPriority;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public class UDiscovery {
    public static final UEntity SERVICE = UEntity.newBuilder()
            .setName("core.udiscovery")
            .setVersionMajor(3)
            .build();
    public static final String METHOD_LOOKUP_URI = "LookupUri";
    public static final String METHOD_UPDATE_NODE = "UpdateNode";
    public static final String METHOD_FIND_NODES = "FindNodes";
    public static final String METHOD_FIND_NODE_PROPERTIES = "FindNodeProperties";
    public static final String METHOD_DELETE_NODES = "DeleteNodes";
    public static final String METHOD_ADD_NODES = "AddNodes";
    public static final String METHOD_UPDATE_PROPERTY = "UpdateProperty";
    public static final String METHOD_REGISTER_FOR_NOTIFICATIONS = "RegisterForNotifications";
    public static final String METHOD_UNREGISTER_FOR_NOTIFICATIONS = "UnregisterForNotifications";
    public static final String METHOD_RESOLVE_URI = "ResolveUri";

    private static final CallOptions DEFAULT_OPTIONS = CallOptions.newBuilder()
            .setPriority(UPriority.UPRIORITY_CS4)
            .setTtl(10_000)
            .build();

    private UDiscovery() {}

    public static UDiscovery.Stub newStub(RpcClient proxy) {
        return newStub(proxy, null, DEFAULT_OPTIONS);
    }

    public static UDiscovery.Stub newStub(RpcClient proxy, CallOptions options) {
        return newStub(proxy, null, options);
    }

    public static UDiscovery.Stub newStub(RpcClient proxy, UAuthority authority, CallOptions options) {
        return new UDiscovery.Stub(proxy, authority, options);
    }

    public static class Stub {
        private final RpcClient proxy;
        private final UAuthority authority;
        private final CallOptions options;

        private Stub(RpcClient proxy, UAuthority authority, CallOptions options) {
            this.proxy = proxy;
            this.authority = authority;
            this.options = options;
        }

        private UUri buildUri(String method) {
            final UUri.Builder builder = UUri.newBuilder()
                    .setEntity(SERVICE)
                    .setResource(UResourceBuilder.forRpcRequest(method));
            if (authority != null) {
                builder.setAuthority(authority);
            }
            return builder.build();
        }

        public Optional<UAuthority> getAuthority() {
            return (authority != null) ? Optional.of(authority) : Optional.empty();
        }

        public CallOptions getOptions() {
            return options;
        }

        public CompletionStage<LookupUriResponse> lookupUri(UUri request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_LOOKUP_URI), packToAny(request), options), LookupUriResponse.class);
        }

        public CompletionStage<UStatus> updateNode(UpdateNodeRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_UPDATE_NODE), packToAny(request), options), UStatus.class);
        }

        public CompletionStage<FindNodesResponse> findNodes(FindNodesRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_FIND_NODES), packToAny(request), options), FindNodesResponse.class);
        }

        public CompletionStage<FindNodePropertiesResponse> findNodeProperties(FindNodePropertiesRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_FIND_NODE_PROPERTIES), packToAny(request), options), FindNodePropertiesResponse.class);
        }

        public CompletionStage<UStatus> deleteNodes(DeleteNodesRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_DELETE_NODES), packToAny(request), options), UStatus.class);
        }

        public CompletionStage<UStatus> addNodes(AddNodesRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_ADD_NODES), packToAny(request), options), UStatus.class);
        }

        public CompletionStage<UStatus> updateProperty(UpdatePropertyRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_UPDATE_PROPERTY), packToAny(request), options), UStatus.class);
        }

        public CompletionStage<UStatus> registerForNotifications(NotificationsRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_REGISTER_FOR_NOTIFICATIONS), packToAny(request), options), UStatus.class);
        }

        public CompletionStage<UStatus> unregisterForNotifications(NotificationsRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_UNREGISTER_FOR_NOTIFICATIONS), packToAny(request), options), UStatus.class);
        }

        public CompletionStage<ResolveUriResponse> resolveUri(ResolveUriRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_RESOLVE_URI), packToAny(request), options), ResolveUriResponse.class);
        }
    }
}
