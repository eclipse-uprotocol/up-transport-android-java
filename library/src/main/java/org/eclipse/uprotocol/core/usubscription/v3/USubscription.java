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

import static org.eclipse.uprotocol.rpc.RpcMapper.mapResponse;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;

import org.eclipse.uprotocol.rpc.CallOptions;
import org.eclipse.uprotocol.rpc.RpcClient;
import org.eclipse.uprotocol.uri.builder.UResourceBuilder;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public class USubscription {
    public static final UEntity SERVICE = UEntity.newBuilder()
            .setName("core.usubscription")
            .setVersionMajor(3)
            .build();
    public static final String METHOD_SUBSCRIBE = "Subscribe";
    public static final String METHOD_UNSUBSCRIBE = "Unsubscribe";
    public static final String METHOD_FETCH_SUBSCRIPTIONS = "FetchSubscriptions";
    public static final String METHOD_CREATE_TOPIC = "CreateTopic";
    public static final String METHOD_DEPRECATE_TOPIC = "DeprecateTopic";
    public static final String METHOD_REGISTER_FOR_NOTIFICATIONS = "RegisterForNotifications";
    public static final String METHOD_UNREGISTER_FOR_NOTIFICATIONS = "UnregisterForNotifications";
    public static final String METHOD_FETCH_SUBSCRIBERS = "FetchSubscribers";
    public static final String METHOD_RESET = "Reset";

    private USubscription() {}

    public static Stub newStub(RpcClient proxy) {
        return newStub(proxy, null, CallOptions.DEFAULT);
    }

    public static Stub newStub(RpcClient proxy, CallOptions options) {
        return newStub(proxy, null, options);
    }

    public static Stub newStub(RpcClient proxy, UAuthority authority, CallOptions options) {
        return new Stub(proxy, authority, options);
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

        public CompletionStage<SubscriptionResponse> subscribe(SubscriptionRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_SUBSCRIBE), packToAny(request), options), SubscriptionResponse.class);
        }

        public CompletionStage<UStatus> unsubscribe(UnsubscribeRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_UNSUBSCRIBE), packToAny(request), options), UStatus.class);
        }

        public CompletionStage<FetchSubscriptionsResponse> fetchSubscriptions(FetchSubscriptionsRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_FETCH_SUBSCRIPTIONS), packToAny(request), options), FetchSubscriptionsResponse.class);
        }

        public CompletionStage<UStatus> createTopic(CreateTopicRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_CREATE_TOPIC), packToAny(request), options), UStatus.class);
        }

        public CompletionStage<UStatus> deprecateTopic(DeprecateTopicRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_DEPRECATE_TOPIC), packToAny(request), options), UStatus.class);
        }

        public CompletionStage<UStatus> registerForNotifications(NotificationsRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_REGISTER_FOR_NOTIFICATIONS), packToAny(request), options), UStatus.class);
        }

        public CompletionStage<UStatus> unregisterForNotifications(NotificationsRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_UNREGISTER_FOR_NOTIFICATIONS), packToAny(request), options), UStatus.class);
        }

        public CompletionStage<FetchSubscribersResponse> fetchSubscribers(FetchSubscribersRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_FETCH_SUBSCRIBERS), packToAny(request), options), FetchSubscribersResponse.class);
        }

        public CompletionStage<UStatus> reset(ResetRequest request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_RESET), packToAny(request), options), UStatus.class);
        }
    }
}
