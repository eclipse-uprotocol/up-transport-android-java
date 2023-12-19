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

import static com.google.common.base.Strings.isNullOrEmpty;

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgumentPositive;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkNotNull;
import static org.eclipse.uprotocol.common.util.UStatusUtils.isOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toCode;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.transport.validate.UAttributesValidator.getValidator;
import static org.eclipse.uprotocol.uri.validator.UriValidator.isEmpty;

import static java.util.Optional.ofNullable;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.client.BuildConfig;
import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.ubus.ConnectionCallback;
import org.eclipse.uprotocol.core.ubus.UBusClient;
import org.eclipse.uprotocol.internal.HandlerExecutor;
import org.eclipse.uprotocol.rpc.CallOptions;
import org.eclipse.uprotocol.rpc.RpcClient;
import org.eclipse.uprotocol.rpc.RpcServer;
import org.eclipse.uprotocol.rpc.URpcListener;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.transport.UTransport;
import org.eclipse.uprotocol.transport.builder.UAttributesBuilder;
import org.eclipse.uprotocol.transport.validate.UAttributesValidator;
import org.eclipse.uprotocol.uri.builder.UResourceBuilder;
import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UPriority;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUID;
import org.eclipse.uprotocol.v1.UUri;
import org.eclipse.uprotocol.validation.ValidationResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The uProtocol client API layer which enables communication over the uBus,
 * offering basic functionalities for establishing connections, sending and
 * receiving messages, and invoking RPC methods.
 */
@SuppressWarnings({"java:S1192", "java:S3398", "java:S6539"})
public final class ULink implements UTransport, RpcServer, RpcClient {
    public static final String TAG_GROUP = "uLink";

    /**
     * The permission necessary to connect to the uBus.
     */
    public static final String PERMISSION_ACCESS_UBUS = "uprotocol.permission.ACCESS_UBUS";

    /**
     * The name of the <code>meta-data</code> element that must be present on an
     * <code>application</code> or <code>service</code> element in a manifest to specify
     * the name of a client (uEntity).
     */
    public static final String META_DATA_ENTITY_NAME = "uprotocol.entity.name";

    /**
     * The name of the <code>meta-data</code> element that must be present on an
     * <code>application</code> or <code>service</code> element in a manifest to specify
     * the major version of a client (uEntity).
     */
    public static final String META_DATA_ENTITY_VERSION = "uprotocol.entity.version";

    /**
     * The name of the <code>meta-data</code> element that may be present on an
     * <code>application</code> or <code>service</code> element in a manifest to specify
     * the id of a client (uEntity).
     */
    public static final String META_DATA_ENTITY_ID = "uprotocol.entity.id";

    private static final String MESSAGE_RECEIVED = "Message received";
    private static final String MESSAGE_DROPPED = "Message dropped";

    private final UUri mClientUri;
    private final UUri mResponseUri;
    private final UBusClient mClient;
    private final Executor mCallbackExecutor;
    private final ServiceLifecycleListener mServiceLifecycleListener;

    private final ConcurrentHashMap<UUID, CompletableFuture<UPayload>> mRequests = new ConcurrentHashMap<>();
    private final Object mRegistrationLock = new Object();
    @GuardedBy("mRegistrationLock")
    private final Map<UUri, URpcListener> mRequestListeners = new HashMap<>();
    @GuardedBy("mRegistrationLock")
    private final Map<UUri, Set<UListener>> mListeners = new HashMap<>();
    @GuardedBy("mRegistrationLock")
    private boolean mRegistrationExpired;

    private final String mTag;
    private boolean mVerboseLoggable;

    private final ConnectionCallback mConnectionCallback = new ConnectionCallback() {
        @Override
        public void onConnected() {
            mCallbackExecutor.execute(() -> {
                renewRegistration();
                mServiceLifecycleListener.onLifecycleChanged(ULink.this, true);
            });
        }

        @Override
        public void onDisconnected() {
            mCallbackExecutor.execute(() -> {
                release();
                mServiceLifecycleListener.onLifecycleChanged(ULink.this, false);
            });
        }

        @Override
        public void onConnectionInterrupted() {
            mCallbackExecutor.execute(() -> {
                setRegistrationExpired();
                mServiceLifecycleListener.onLifecycleChanged(ULink.this, false);
            });
        }
    };

    private final UListener mListener = new UListener() {
        @Override
        public void onReceive(UUri topic, UPayload payload, UAttributes attributes) {
            handleMessage(buildMessage(topic, payload, attributes));
        }

        @Override
        public void onReceive(UMessage message) {
            handleMessage(message);
        }
    };

    /**
     * Callback to notify the lifecycle of the uBus.
     *
     * <p>Access to the uBus should happen
     * after {@link ServiceLifecycleListener#onLifecycleChanged(ULink, boolean)} call with
     * {@code ready} set {@code true}.
     */
    public interface ServiceLifecycleListener {
        /**
         * The uBus has gone through status change.
         *
         * @param link  A {@link ULink} object that was originally associated with this
         *              listener from {@link #create(Context, Handler, ServiceLifecycleListener)} call.
         * @param ready When {@code true}, the uBus is ready and all accesses are ok.
         *              Otherwise it has crashed or killed and will be restarted.
         */
        void onLifecycleChanged(@NonNull ULink link, boolean ready);
    }

    @VisibleForTesting
    ULink(@NonNull Context context, @Nullable UEntity entity, @Nullable UBusClient client, @Nullable Executor executor,
            @Nullable ServiceLifecycleListener listener) {
        checkNonNullContext(context);
        entity = checkContainsEntity(getPackageInfo(context), entity);
        mClientUri = UUri.newBuilder()
                .setEntity(entity)
                .build();
        mResponseUri = UUri.newBuilder(mClientUri)
                .setResource(UResourceBuilder.forRpcResponse())
                .build();
        mClient = ofNullable(client).orElse(new UBusClient(context, entity, mConnectionCallback, mListener));
        mCallbackExecutor = ofNullable(executor).orElse(context.getMainExecutor());
        mServiceLifecycleListener = ofNullable(listener).orElse((link, ready) -> {});

        mTag = tag(entity.getName(), TAG_GROUP);
        mVerboseLoggable = Log.isLoggable(mTag, Log.VERBOSE);
        if (mVerboseLoggable) {
            Log.v(mTag, join(Key.PACKAGE, BuildConfig.LIBRARY_PACKAGE_NAME, Key.VERSION, BuildConfig.VERSION_NAME));
        }
    }

    private static void checkNonNullContext(Context context) {
        checkNotNull(context, "Context is null");
        if (context instanceof ContextWrapper contextWrapper && contextWrapper.getBaseContext() == null) {
            throw new NullPointerException("ContextWrapper with null base passed as Context");
        }
    }

    private static @NonNull PackageInfo getPackageInfo(@NonNull Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(),
                    PackageManager.GET_SERVICES | PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            throw new SecurityException(e.getMessage(), e);
        }
    }

    private static @NonNull UEntity checkContainsEntity(@NonNull PackageInfo packageInfo, @Nullable UEntity entity) {
        return Stream.concat(Stream.of(packageInfo.applicationInfo),
                        (packageInfo.services != null) ? Stream.of(packageInfo.services) : Stream.empty())
                .filter(Objects::nonNull)
                .map(info -> {
                    final UEntity foundEntity = getEntity(info);
                    if (entity != null) {
                        return entity.equals(foundEntity) ? entity : null;
                    } else {
                        return foundEntity;
                    }
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new SecurityException("Missing or not matching '" + META_DATA_ENTITY_NAME + "', '" +
                        META_DATA_ENTITY_VERSION + "' or '" + META_DATA_ENTITY_ID + "' meta-data in manifest"));
    }

    private static @Nullable UEntity getEntity(@NonNull PackageItemInfo info) {
        if (info.metaData != null) {
            final String name = info.metaData.getString(META_DATA_ENTITY_NAME);
            final int version = info.metaData.getInt(META_DATA_ENTITY_VERSION);
            final int id = info.metaData.getInt(META_DATA_ENTITY_ID);
            if (!isNullOrEmpty(name) && version > 0) {
                final UEntity.Builder builder = UEntity.newBuilder()
                        .setName(name)
                        .setVersionMajor(version);
                if (id > 0) {
                    builder.setId(id);
                }
                return builder.build();
            }
        }
        return null;
    }

    /**
     * Create an instance.
     *
     * @param context  An application {@link Context}. This should not be {@code null}. If you are passing
     *                 {@link ContextWrapper}, make sure that its base Context is non-null as well.
     *                 Otherwise it will throw {@link NullPointerException}.
     * @param handler  A {@link Handler} on which callbacks should execute, or {@code null} to execute on
     *                 the application's main thread.
     * @param listener A {@link ServiceLifecycleListener} for monitoring the uBus lifecycle.
     * @return A {@link ULink} instance.
     * @throws SecurityException If the caller does not have {@value META_DATA_ENTITY_NAME} and
     *         {@value META_DATA_ENTITY_VERSION} <code>meta-data</code> elements declared in the manifest.
     * @see #META_DATA_ENTITY_NAME
     * @see #META_DATA_ENTITY_VERSION
     */
    public static @NonNull ULink create(@NonNull Context context, @Nullable Handler handler,
            @Nullable ServiceLifecycleListener listener) {
        return new ULink(context, null, null, new HandlerExecutor(handler), listener);
    }

    /**
     * Create an instance for a specified client (uEntity).
     *
     * @param context  An application {@link Context}. This should not be {@code null}. If you are passing
     *                 {@link ContextWrapper}, make sure that its base Context is non-null as well.
     *                 Otherwise it will throw {@link NullPointerException}.
     * @param entity   A {@link UEntity} containing its name and major version, or {@code null} to use the
     *                 first found declaration under <code>application</code> or <code>service</code> element
     *                 in a manifest.
     * @param handler  A {@link Handler} on which callbacks should execute, or {@code null} to execute on
     *                 the application's main thread.
     * @param listener A {@link ServiceLifecycleListener} for monitoring the uBus lifecycle.
     * @return A {@link ULink} instance.
     * @throws SecurityException If the caller does not have {@value META_DATA_ENTITY_NAME} and
     *         {@value META_DATA_ENTITY_VERSION} <code>meta-data</code> elements declared in the manifest.
     * @see #META_DATA_ENTITY_NAME
     * @see #META_DATA_ENTITY_VERSION
     */
    public static @NonNull ULink create(@NonNull Context context, @Nullable UEntity entity,
            @Nullable Handler handler, @Nullable ServiceLifecycleListener listener) {
        return new ULink(context, entity, null, new HandlerExecutor(handler), listener);
    }

    /**
     * Create an instance.
     *
     * @param context  An application {@link Context}. This should not be {@code null}. If you are passing
     *                 {@link ContextWrapper}, make sure that its base Context is non-null as well.
     *                 Otherwise it will throw {@link NullPointerException}.
     * @param executor An {@link Executor} on which callbacks should execute, or {@code null} to execute on
     *                 the application's main thread.
     * @param listener A {@link ServiceLifecycleListener} for monitoring the uBus lifecycle.
     * @return A {@link ULink} instance.
     * @throws SecurityException If the caller does not have {@value META_DATA_ENTITY_NAME} and
     *         {@value META_DATA_ENTITY_VERSION} <code>meta-data</code> elements declared in the manifest.
     * @see #META_DATA_ENTITY_NAME
     * @see #META_DATA_ENTITY_VERSION
     */
    public static @NonNull ULink create(@NonNull Context context, @Nullable Executor executor,
            @Nullable ServiceLifecycleListener listener) {
        return new ULink(context, null, null, executor, listener);
    }

    /**
     * Create an instance.
     *
     * @param context  An application {@link Context}. This should not be {@code null}. If you are passing
     *                 {@link ContextWrapper}, make sure that its base Context is non-null as well.
     *                 Otherwise it will throw {@link NullPointerException}.
     * @param entity   A {@link UEntity} containing its name and major version, or {@code null} to use the
     *                 first found declaration under <code>application</code> or <code>service</code> element
     *                 in a manifest.
     * @param executor An {@link Executor} on which callbacks should execute, or {@code null} to execute on
     *                 the application's main thread.
     * @param listener A {@link ServiceLifecycleListener} for monitoring the uBus lifecycle.
     * @return A {@link ULink} instance.
     * @throws SecurityException If the caller does not have {@value META_DATA_ENTITY_NAME} and
     *         {@value META_DATA_ENTITY_VERSION} <code>meta-data</code> elements declared in the manifest.
     * @see #META_DATA_ENTITY_NAME
     * @see #META_DATA_ENTITY_VERSION
     */
    public static @NonNull ULink create(@NonNull Context context, @Nullable UEntity entity,
            @Nullable Executor executor, @Nullable ServiceLifecycleListener listener) {
        return new ULink(context, entity, null, executor, listener);
    }

    /**
     * Connect to the uBus.
     *
     * <p>Requires {@value #PERMISSION_ACCESS_UBUS} permission to access this API.
     *
     * <p>An instance connected with this method should be disconnected from the uBus by calling
     * {@link #disconnect()} before the passed {@link Context} is released.
     *
     * @return A {@link CompletionStage<UStatus>} used by a caller to receive the connection status.
     */
    public @NonNull CompletionStage<UStatus> connect() {
        return mClient.connect();
    }

    /**
     * Disconnect from the uBus.
     *
     * <p>All previously registered listeners will be automatically unregistered.
     *
     * @return A {@link CompletionStage<UStatus>} used by a caller to receive the disconnection status.
     */
    public @NonNull CompletionStage<UStatus> disconnect() {
        return mClient.disconnect();
    }

    /**
     * Check whether this instance is disconnected from the uBus or not.
     *
     * @return {@code true} if it is disconnected.
     */
    public boolean isDisconnected() {
        return mClient.isDisconnected();
    }

    /**
     * Check whether this instance is already connecting to the uBus or not.
     *
     * @return {@code true} if it is connecting.
     */
    public boolean isConnecting() {
        return mClient.isConnecting();
    }

    /**
     * Check whether the uBus is connected or not. This will return {@code false} if it
     * is still connecting.
     *
     * @return {@code true} if is is connected.
     */
    public boolean isConnected() {
        return mClient.isConnected();
    }

    private void setRegistrationExpired() {
        synchronized (mRegistrationLock) {
            mRegistrationExpired = true;
        }
    }

    private void renewRegistration() {
        synchronized (mRegistrationLock) {
            if (mRegistrationExpired) {
                mRequestListeners.keySet().forEach(mClient::enableDispatching);
                mListeners.keySet().forEach(mClient::enableDispatching);
                mRegistrationExpired = false;
            }
        }
    }

    private void release() {
        synchronized (mRegistrationLock) {
            mRequests.values().forEach(requestFuture -> requestFuture.completeExceptionally(
                    new UStatusException(UCode.CANCELLED, "Service is disconnected")));
            mRequests.clear();
            mRequestListeners.clear();
            mListeners.clear();
            mRegistrationExpired = false;
        }
    }

    /**
     * Get a client (uEntity) who is holding this instance.
     *
     * @return A {@link UEntity}.
     */
    public @NonNull UEntity getEntity() {
        return mClientUri.getEntity();
    }

    /**
     * Get a URI of a client who is holding this instance.
     *
     * @return A {@link UUri}.
     */
    public @NonNull UUri getClientUri() {
        return mClientUri;
    }

    @VisibleForTesting
    @NonNull String getTag() {
        return mTag;
    }

    @VisibleForTesting
    void setLoggable(int level) {
        mVerboseLoggable = level <= Log.VERBOSE;
    }

    @VisibleForTesting
    ConnectionCallback getConnectionCallback() {
        return mConnectionCallback;
    }

    @VisibleForTesting
    UListener getListener() {
        return mListener;
    }

    private static @NonNull UMessage buildMessage(UUri source, UPayload payload, UAttributes attributes) {
        final UMessage.Builder builder = UMessage.newBuilder();
        if (source != null) {
            builder.setSource(source);
        }
        if (payload != null) {
            builder.setPayload(payload);
        }
        if (attributes != null) {
            builder.setAttributes(attributes);
        }
        return builder.build();
    }

    @Override
    public @NonNull UStatus send(@NonNull UUri source, @NonNull UPayload payload, @NonNull UAttributes attributes) {
        return mClient.send(buildMessage(source, payload, attributes));
    }

    @Override
    public @NonNull UStatus send(@NonNull UMessage message) {
        return mClient.send(message);
    }

    @Override
    public @NonNull UStatus registerListener(@NonNull UUri topic, @NonNull UListener listener) {
        try {
            checkArgument(!isEmpty(topic), "Topic is empty");
            checkNotNull(listener, "Listener is null");
            synchronized (mRegistrationLock) {
                Set<UListener> listeners = mListeners.get(topic);
                if (listeners == null) {
                    listeners = new HashSet<>();
                }
                if (listeners.isEmpty()) {
                    final UStatus status = mClient.enableDispatching(topic);
                    if (!isOk(status)) {
                        return status;
                    }
                    mListeners.put(topic, listeners);
                }
                if (listeners.add(listener) && listeners.size() > 1) {
                    mCallbackExecutor.execute(() -> {
                        final UMessage event = mClient.getLastMessage(topic);
                        if (event != null) {
                            listener.onReceive(event);
                        }
                    });
                }
                return STATUS_OK;
            }
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    @Override
    public @NonNull UStatus unregisterListener(@NonNull UUri topic, @NonNull UListener listener) {
        try {
            checkArgument(!isEmpty(topic), "Topic is empty");
            checkNotNull(listener, "Listener is null");
            synchronized (mRegistrationLock) {
                if (unregisterListenerLocked(topic, listener)) {
                    mListeners.remove(topic);
                }
            }
            return STATUS_OK;
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    /**
     * Unregister a listener from all topics.
     *
     * <p>If this listener wasn't registered, nothing will happen.
     *
     * @param listener A {@link UListener} which needs to be unregistered.
     * @return A {@link UStatus} which contains a result code and other details.
     */
    public @NonNull UStatus unregisterListener(@NonNull UListener listener) {
        try {
            checkNotNull(listener, "Listener is null");
            synchronized (mRegistrationLock) {
                mListeners.keySet().removeIf(topic -> unregisterListenerLocked(topic, listener));
            }
            return STATUS_OK;
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    private boolean unregisterListenerLocked(@NonNull UUri topic, @NonNull UListener listener) {
        final Set<UListener> listeners = mListeners.get(topic);
        if (listeners != null && listeners.contains(listener)) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                // No listener left for this topic
                mClient.disableDispatchingQuietly(topic);
                return true; // The entry MUST be removed
            }
        }
        return false;
    }

    @Override
    public @NonNull UStatus registerRpcListener(@NonNull UUri methodUri, @NonNull URpcListener listener) {
        try {
            checkArgument(!isEmpty(methodUri), "Method URI is empty");
            checkNotNull(listener, "Listener is null");
            synchronized (mRegistrationLock) {
                final URpcListener currentListener = mRequestListeners.get(methodUri);
                if (currentListener == listener) {
                    return STATUS_OK;
                }
                checkArgument(currentListener == null, UCode.ALREADY_EXISTS, "Listener is already registered");
                final UStatus status = mClient.enableDispatching(methodUri);
                if (isOk(status)) {
                    mRequestListeners.put(methodUri, listener);
                }
                return status;
            }
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    @Override
    public @NonNull UStatus unregisterRpcListener(@NonNull UUri methodUri, @NonNull URpcListener listener) {
        try {
            checkArgument(!isEmpty(methodUri), "Method URI is empty");
            checkNotNull(listener, "Listener is null");
            synchronized (mRegistrationLock) {
                if (mRequestListeners.remove(methodUri, listener)) {
                    mClient.disableDispatchingQuietly(methodUri);
                }
                return STATUS_OK;
            }
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    /**
     * Unregister a listener from all method URIs.
     *
     * <p>If this listener wasn't registered, nothing will happen.
     *
     * @param listener A {@link URpcListener} which needs to be unregistered.
     * @return A {@link UStatus} which contains a result code and other details.
     */
    public @NonNull UStatus unregisterRpcListener(@NonNull URpcListener listener) {
        try {
            checkNotNull(listener, "Listener is null");
            synchronized (mRegistrationLock) {
                mRequestListeners.keySet().removeIf(methodUri -> {
                    if (mRequestListeners.get(methodUri) != listener) {
                        return false;
                    }
                    mClient.disableDispatchingQuietly(methodUri);
                    return true;
                });
                return STATUS_OK;
            }
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    @Override
    public @NonNull CompletionStage<UPayload> invokeMethod(@NonNull UUri methodUri, @NonNull UPayload requestPayload,
            @NonNull CallOptions options) {
        try {
            checkArgument(!isEmpty(methodUri), "Method URI is empty");
            checkNotNull(requestPayload, "Payload is null");
            checkNotNull(options, "Options cannot be null");
            final int timeout = checkArgumentPositive(options.timeout(), "Timeout is not positive");
            final UAttributesBuilder builder = UAttributesBuilder.request(UPriority.UPRIORITY_CS4, methodUri, timeout);
            options.token().ifPresent(builder::withToken);
            final UMessage requestMessage = buildMessage(mResponseUri, requestPayload, builder.build());
            return mRequests.compute(requestMessage.getAttributes().getId(), (requestId, currentRequest) -> {
                checkArgument(currentRequest == null, UCode.ABORTED, "Duplicated request found");
                final UStatus status = send(requestMessage);
                if (isOk(status)) {
                    return buildClientResponseFuture(requestMessage);
                } else {
                    throw new UStatusException(status);
                }
            });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private @NonNull CompletableFuture<UPayload> buildClientResponseFuture(@NonNull UMessage requestMessage) {
        final CompletableFuture<UPayload> responseFuture = new CompletableFuture<UPayload>()
                .orTimeout(requestMessage.getAttributes().getTtl(), TimeUnit.MILLISECONDS);
        responseFuture.whenComplete((responsePayload, exception) ->
            mRequests.remove(requestMessage.getAttributes().getId()));
        return responseFuture;
    }

    private void handleMessage(@NonNull UMessage message) {
        if (mVerboseLoggable) {
            Log.v(mTag, join(Key.EVENT, MESSAGE_RECEIVED, Key.MESSAGE, stringify(message)));
        }
        final UAttributes attributes = message.getAttributes();
        final UAttributesValidator validator = getValidator(attributes);
        final ValidationResult result = validator.validate(attributes);
        if (result.isFailure()) {
            Log.w(mTag, join(Key.EVENT, MESSAGE_DROPPED, Key.MESSAGE, stringify(message), Key.REASON, result.getMessage()));
            return;
        }
        if (validator.isExpired(attributes)) { // Do we need to check expiration? Should be done by the service...
            Log.w(mTag, join(Key.EVENT, MESSAGE_DROPPED, Key.MESSAGE, stringify(message), Key.REASON, "Expired"));
            return;
        }
        switch (attributes.getType()) {
            case UMESSAGE_TYPE_PUBLISH -> handleGenericMessage(message);
            case UMESSAGE_TYPE_REQUEST -> handleRequestMessage(message);
            case UMESSAGE_TYPE_RESPONSE -> handleResponseMessage(message);
            default -> Log.w(mTag, join(Key.EVENT, MESSAGE_DROPPED, Key.MESSAGE, stringify(message), Key.REASON, "Unknown type"));
        }
    }

    private void handleGenericMessage(@NonNull UMessage message) {
        if (message.getAttributes().hasSink()) {
            final UEntity entity = message.getAttributes().getSink().getEntity();
            if (!entity.equals(mClientUri.getEntity())) {
                Log.w(mTag, join(Key.EVENT, MESSAGE_DROPPED, Key.MESSAGE, stringify(message), Key.REASON, "Wrong sink"));
                return;
            }
        }
        mCallbackExecutor.execute(() -> {
            final UUri topic = message.getSource();
            final Set<UListener> listeners;
            synchronized (mRegistrationLock) {
                listeners = new ArraySet<>(mListeners.get(topic));
                if (listeners.isEmpty()) {
                    Log.w(mTag, join(Key.EVENT, MESSAGE_DROPPED, Key.MESSAGE, stringify(message), Key.REASON, "No listener"));
                }
            }
            listeners.forEach(listener -> listener.onReceive(message));
        });
    }

    private void handleRequestMessage(@NonNull UMessage requestMessage) {
        mCallbackExecutor.execute(() -> {
            final UUri methodUri = requestMessage.getAttributes().getSink();
            final URpcListener listener;
            synchronized (mRegistrationLock) {
                listener = mRequestListeners.get(methodUri);
                if (listener == null) {
                    Log.w(mTag, join(Key.EVENT, MESSAGE_DROPPED, Key.MESSAGE, stringify(requestMessage), Key.REASON, "No listener"));
                    return;
                }
            }
            listener.onReceive(requestMessage, buildServerResponseFuture(requestMessage));
        });
    }

    private @NonNull CompletableFuture<UPayload> buildServerResponseFuture(@NonNull UMessage requestMessage) {
        final CompletableFuture<UPayload> responseFuture = new CompletableFuture<>();
        responseFuture.whenComplete((responsePayload, exception) -> {
            final UAttributes requestAttributes = requestMessage.getAttributes();
            final UAttributesBuilder builder = UAttributesBuilder
                    .response(requestAttributes.getPriority(), requestMessage.getSource(), requestAttributes.getId());
            if (exception != null) {
                builder.withCommStatus(toStatus(exception).getCodeValue());
            } else if (responsePayload == null) {
                return;
            }
            send(buildMessage(requestAttributes.getSink(), responsePayload, builder.build()));
        });
        return responseFuture;
    }

    private void handleResponseMessage(@NonNull UMessage responseMessage) {
        final UAttributes responseAttributes = responseMessage.getAttributes();
        final CompletableFuture<UPayload> responseFuture = mRequests.remove(responseAttributes.getReqid());
        if (responseFuture == null) {
            return;
        }
        if (responseAttributes.hasCommstatus()) {
            final UCode code = toCode(responseAttributes.getCommstatus());
            if (code != UCode.OK) {
                responseFuture.completeExceptionally(new UStatusException(code, "Communication error [" + code + "]"));
                return;
            }
        }
        responseFuture.complete(responseMessage.getPayload());
    }
}
