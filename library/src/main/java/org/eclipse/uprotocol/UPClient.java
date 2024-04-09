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
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.transport.validate.UAttributesValidator.getValidator;
import static org.eclipse.uprotocol.uri.validator.UriValidator.isEmpty;
import static org.eclipse.uprotocol.uri.validator.UriValidator.isRpcMethod;

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
import org.eclipse.uprotocol.core.ubus.UBusManager;
import org.eclipse.uprotocol.internal.HandlerExecutor;
import org.eclipse.uprotocol.rpc.RpcClient;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.transport.UTransport;
import org.eclipse.uprotocol.transport.builder.UAttributesBuilder;
import org.eclipse.uprotocol.transport.validate.UAttributesValidator;
import org.eclipse.uprotocol.uri.factory.UResourceBuilder;
import org.eclipse.uprotocol.v1.CallOptions;
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
public final class UPClient implements UTransport, RpcClient {
    /**
     * The logging group tag used by this class and all sub-components.
     */
    public static final String TAG_GROUP = "uPClient";

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

    private final UUri mUri;
    private final UUri mResponseUri;
    private final UBusManager mUBusManager;
    private final Executor mCallbackExecutor;
    private final ServiceLifecycleListener mServiceLifecycleListener;

    private final ConcurrentHashMap<UUID, CompletableFuture<UMessage>> mRequests = new ConcurrentHashMap<>();
    private final Object mRegistrationLock = new Object();
    @GuardedBy("mRegistrationLock")
    private final Map<UUri, Set<UListener>> mGenericListeners = new HashMap<>();
    @GuardedBy("mRegistrationLock")
    private final Map<UUri, UListener> mRequestListeners = new HashMap<>();
    @GuardedBy("mRegistrationLock")
    private boolean mRegistrationExpired;

    private final String mTag;
    private boolean mVerboseLoggable;

    private final ConnectionCallback mConnectionCallback = new ConnectionCallback() {
        @Override
        public void onConnected() {
            mCallbackExecutor.execute(() -> {
                renewRegistration();
                mServiceLifecycleListener.onLifecycleChanged(UPClient.this, true);
            });
        }

        @Override
        public void onDisconnected() {
            mCallbackExecutor.execute(() -> {
                release();
                mServiceLifecycleListener.onLifecycleChanged(UPClient.this, false);
            });
        }

        @Override
        public void onConnectionInterrupted() {
            mCallbackExecutor.execute(() -> {
                setRegistrationExpired();
                mServiceLifecycleListener.onLifecycleChanged(UPClient.this, false);
            });
        }
    };

    private final UListener mListener = this::handleMessage;

    /**
     * The callback to notify the lifecycle of the uBus.
     *
     * <p>Access to the uBus should happen
     * after {@link ServiceLifecycleListener#onLifecycleChanged(UPClient, boolean)} call with
     * <code>ready</code> set <code>true</code>.
     */
    public interface ServiceLifecycleListener {
        /**
         * The uBus has gone through status change.
         *
         * @param client A {@link UPClient} object that was originally associated with this
         *               listener from {@link #create(Context, Handler, ServiceLifecycleListener)} call.
         * @param ready  When <code>true</code>, the uBus is ready and all accesses are ok.
         *               Otherwise it has crashed or killed and will be restarted.
         */
        void onLifecycleChanged(@NonNull UPClient client, boolean ready);
    }

    @VisibleForTesting
    UPClient(@NonNull Context context, @Nullable UEntity entity, @Nullable UBusManager manager,
            @Nullable Executor executor, @Nullable ServiceLifecycleListener listener) {
        checkNonNullContext(context);
        entity = checkContainsEntity(getPackageInfo(context), entity);
        mUri = UUri.newBuilder()
                .setEntity(entity)
                .build();
        mResponseUri = UUri.newBuilder(mUri)
                .setResource(UResourceBuilder.forRpcResponse())
                .build();
        mUBusManager = ofNullable(manager).orElse(new UBusManager(context, entity, mConnectionCallback, mListener));
        mCallbackExecutor = ofNullable(executor).orElse(context.getMainExecutor());
        mServiceLifecycleListener = ofNullable(listener).orElse((client, ready) -> {});

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
     * @param context  An application {@link Context}. This should not be <code>null</code>. If you are passing
     *                 {@link ContextWrapper}, make sure that its base Context is non-null as well.
     *                 Otherwise it will throw {@link NullPointerException}.
     * @param handler  A {@link Handler} on which callbacks should execute, or <code>null</code> to execute on
     *                 the application's main thread.
     * @param listener A {@link ServiceLifecycleListener} for monitoring the uBus lifecycle.
     * @return A {@link UPClient} instance.
     * @throws SecurityException If the caller does not have {@link #META_DATA_ENTITY_NAME} and
     *         {@link #META_DATA_ENTITY_VERSION} <code>meta-data</code> elements declared in the manifest.
     */
    public static @NonNull UPClient create(@NonNull Context context, @Nullable Handler handler,
            @Nullable ServiceLifecycleListener listener) {
        return new UPClient(context, null, null, new HandlerExecutor(handler), listener);
    }

    /**
     * Create an instance for a specified uEntity.
     *
     * @param context  An application {@link Context}. This should not be <code>null</code>. If you are passing
     *                 {@link ContextWrapper}, make sure that its base Context is non-null as well.
     *                 Otherwise it will throw {@link NullPointerException}.
     * @param entity   A {@link UEntity} containing its name and major version, or <code>null</code> to use the
     *                 first found declaration under <code>application</code> or <code>service</code> element
     *                 in a manifest.
     * @param handler  A {@link Handler} on which callbacks should execute, or <code>null</code> to execute on
     *                 the application's main thread.
     * @param listener A {@link ServiceLifecycleListener} for monitoring the uBus lifecycle.
     * @return A {@link UPClient} instance.
     * @throws SecurityException If the caller does not have {@link #META_DATA_ENTITY_NAME} and
     *         {@link #META_DATA_ENTITY_VERSION} <code>meta-data</code> elements declared in the manifest.
     */
    public static @NonNull UPClient create(@NonNull Context context, @Nullable UEntity entity,
            @Nullable Handler handler, @Nullable ServiceLifecycleListener listener) {
        return new UPClient(context, entity, null, new HandlerExecutor(handler), listener);
    }

    /**
     * Create an instance.
     *
     * @param context  An application {@link Context}. This should not be <code>null</code>. If you are passing
     *                 {@link ContextWrapper}, make sure that its base Context is non-null as well.
     *                 Otherwise it will throw {@link NullPointerException}.
     * @param executor An {@link Executor} on which callbacks should execute, or <code>null</code> to execute on
     *                 the application's main thread.
     * @param listener A {@link ServiceLifecycleListener} for monitoring the uBus lifecycle.
     * @return A {@link UPClient} instance.
     * @throws SecurityException If the caller does not have {@link #META_DATA_ENTITY_NAME} and
     *         {@link #META_DATA_ENTITY_VERSION} <code>meta-data</code> elements declared in the manifest.
     */
    public static @NonNull UPClient create(@NonNull Context context, @Nullable Executor executor,
            @Nullable ServiceLifecycleListener listener) {
        return new UPClient(context, null, null, executor, listener);
    }

    /**
     * Create an instance for a specified uEntity.
     *
     * @param context  An application {@link Context}. This should not be <code>null</code>. If you are passing
     *                 {@link ContextWrapper}, make sure that its base Context is non-null as well.
     *                 Otherwise it will throw {@link NullPointerException}.
     * @param entity   A {@link UEntity} containing its name and major version, or <code>null</code> to use the
     *                 first found declaration under <code>application</code> or <code>service</code> element
     *                 in a manifest.
     * @param executor An {@link Executor} on which callbacks should execute, or <code>null</code> to execute on
     *                 the application's main thread.
     * @param listener A {@link ServiceLifecycleListener} for monitoring the uBus lifecycle.
     * @return A {@link UPClient} instance.
     * @throws SecurityException If the caller does not have {@link #META_DATA_ENTITY_NAME} and
     *         {@link #META_DATA_ENTITY_VERSION} <code>meta-data</code> elements declared in the manifest.
     */
    public static @NonNull UPClient create(@NonNull Context context, @Nullable UEntity entity,
            @Nullable Executor executor, @Nullable ServiceLifecycleListener listener) {
        return new UPClient(context, entity, null, executor, listener);
    }

    /**
     * Connect to the uBus.
     *
     * <p>Requires {@link #PERMISSION_ACCESS_UBUS} permission to access this API.
     *
     * <p>An instance connected with this method should be disconnected from the uBus by calling
     * {@link #disconnect()} before the passed {@link Context} is released.
     *
     * @return A {@link CompletionStage<UStatus>} used by a caller to receive the connection status.
     */
    public @NonNull CompletionStage<UStatus> connect() {
        return mUBusManager.connect();
    }

    /**
     * Disconnect from the uBus.
     *
     * <p>All previously registered listeners will be automatically unregistered.
     *
     * @return A {@link CompletionStage<UStatus>} used by a caller to receive the disconnection status.
     */
    public @NonNull CompletionStage<UStatus> disconnect() {
        return mUBusManager.disconnect();
    }

    /**
     * Check whether this instance is disconnected from the uBus or not.
     *
     * @return <code>true</code> if it is disconnected.
     */
    public boolean isDisconnected() {
        return mUBusManager.isDisconnected();
    }

    /**
     * Check whether this instance is already connecting to the uBus or not.
     *
     * @return <code>true</code> if it is connecting.
     */
    public boolean isConnecting() {
        return mUBusManager.isConnecting();
    }

    /**
     * Check whether the uBus is connected or not. This will return <code>false</code> if it
     * is still connecting.
     *
     * @return <code>true</code> if is is connected.
     */
    public boolean isConnected() {
        return mUBusManager.isConnected();
    }

    private void setRegistrationExpired() {
        synchronized (mRegistrationLock) {
            mRegistrationExpired = true;
        }
    }

    private void renewRegistration() {
        synchronized (mRegistrationLock) {
            if (mRegistrationExpired) {
                mGenericListeners.keySet().forEach(mUBusManager::enableDispatching);
                mRequestListeners.keySet().forEach(mUBusManager::enableDispatching);
                mRegistrationExpired = false;
            }
        }
    }

    private void release() {
        synchronized (mRegistrationLock) {
            mRequests.values().forEach(requestFuture -> requestFuture.completeExceptionally(
                    new UStatusException(UCode.CANCELLED, "Service is disconnected")));
            mRequests.clear();
            mGenericListeners.clear();
            mRequestListeners.clear();
            mRegistrationExpired = false;
        }
    }

    /**
     * Get a uEntity associated with this instance.
     *
     * @return A {@link UEntity}.
     */
    public @NonNull UEntity getEntity() {
        return mUri.getEntity();
    }

    /**
     * Get a URI associated with this instance.
     *
     * @return A {@link UUri}.
     */
    public @NonNull UUri getUri() {
        return mUri;
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


    /**
     * Transmit a message.
     *
     * @param message A {@link UMessage} to be sent.
     * @return A {@link UStatus} which contains a result code and other details.
     */
    @Override
    public @NonNull UStatus send(@NonNull UMessage message) {
        return mUBusManager.send(message);
    }

    /**
     * Register a listener for a particular URI to be notified when a message with that URI is received.
     *
     * <p>Only one listener is allowed to be registered per method URI. For a topic URI,
     * multiple listeners are allowed to be registered. But in order to start receiving
     * published data a client needs to subscribe to that topic.
     *
     * @param uri      A {@link UUri} associated with either topic or method.
     * @param listener A {@link UListener} which needs to be registered.
     * @return A {@link UStatus} which contains a result code and other details.
     */

    @Override
    public @NonNull UStatus registerListener(@NonNull UUri uri, @NonNull UListener listener) {
        return isRpcMethod(uri) ? registerRequestListener(uri, listener) : registerGenericListener(uri, listener);
    }

    /**
     * Unregister a listener from a particular URI.
     *
     * <p>If this listener wasn't registered, nothing will happen.
     *
     * @param uri      A {@link UUri} associated with either topic or method.
     * @param listener A {@link UListener} which needs to be unregistered.
     * @return A {@link UStatus} which contains a result code and other details.
     */
    @Override
    public @NonNull UStatus unregisterListener(@NonNull UUri uri, @NonNull UListener listener) {
        return isRpcMethod(uri) ? unregisterRequestListener(uri, listener) : unregisterGenericListener(uri, listener);
    }

    /**
     * Unregister a listener from all.
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
                mGenericListeners.keySet().removeIf(topic -> unregisterGenericListenerLocked(topic, listener));
                mRequestListeners.entrySet().removeIf(entry -> {
                    if (entry.getValue() == listener) {
                        mUBusManager.disableDispatchingQuietly(entry.getKey());
                        return true;
                    } else {
                        return false;
                    }
                });
            }
            return STATUS_OK;
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    private @NonNull UStatus registerGenericListener(@NonNull UUri topic, @NonNull UListener listener) {
        try {
            checkArgument(!isEmpty(topic), "Topic is empty");
            checkNotNull(listener, "Listener is null");
            synchronized (mRegistrationLock) {
                Set<UListener> listeners = mGenericListeners.get(topic);
                if (listeners == null) {
                    listeners = new HashSet<>();
                }
                if (listeners.isEmpty()) {
                    final UStatus status = mUBusManager.enableDispatching(topic);
                    if (!isOk(status)) {
                        return status;
                    }
                    mGenericListeners.put(topic, listeners);
                }
                if (listeners.add(listener) && listeners.size() > 1) {
                    mCallbackExecutor.execute(() -> {
                        final UMessage event = mUBusManager.getLastMessage(topic);
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

    private @NonNull UStatus unregisterGenericListener(@NonNull UUri topic, @NonNull UListener listener) {
        try {
            checkArgument(!isEmpty(topic), "Topic is empty");
            checkNotNull(listener, "Listener is null");
            synchronized (mRegistrationLock) {
                if (unregisterGenericListenerLocked(topic, listener)) {
                    mGenericListeners.remove(topic);
                }
            }
            return STATUS_OK;
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    private boolean unregisterGenericListenerLocked(@NonNull UUri topic, @NonNull UListener listener) {
        final Set<UListener> listeners = mGenericListeners.get(topic);
        if (listeners != null && listeners.contains(listener)) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                // No listener left for this topic
                mUBusManager.disableDispatchingQuietly(topic);
                return true; // The entry MUST be removed
            }
        }
        return false;
    }

    private @NonNull UStatus registerRequestListener(@NonNull UUri methodUri, @NonNull UListener listener) {
        try {
            checkNotNull(listener, "Listener is null");
            synchronized (mRegistrationLock) {
                final UListener currentListener = mRequestListeners.get(methodUri);
                if (currentListener == listener) {
                    return STATUS_OK;
                }
                checkArgument(currentListener == null, UCode.ALREADY_EXISTS, "Listener is already registered");
                final UStatus status = mUBusManager.enableDispatching(methodUri);
                if (isOk(status)) {
                    mRequestListeners.put(methodUri, listener);
                }
                return status;
            }
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    private @NonNull UStatus unregisterRequestListener(@NonNull UUri methodUri, @NonNull UListener listener) {
        try {
            checkNotNull(listener, "Listener is null");
            synchronized (mRegistrationLock) {
                if (mRequestListeners.remove(methodUri, listener)) {
                    mUBusManager.disableDispatchingQuietly(methodUri);
                }
                return STATUS_OK;
            }
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    /**
     * Asynchronously invoke a method (send an RPC request) and receive a response.
     *
     * @param methodUri      A {@link UUri} associated with a method.
     * @param requestPayload A {@link UPayload} to be supplied with a request.
     * @param options        {@link CallOptions} containing various invocation parameters.
     * @return A {@link CompletionStage<UMessage>} used by a caller to receive a response.
     */
    @Override
    public @NonNull CompletionStage<UMessage> invokeMethod(@NonNull UUri methodUri, @NonNull UPayload requestPayload,
            @NonNull CallOptions options) {
        try {
            checkArgument(!isEmpty(methodUri), "Method URI is empty");
            checkNotNull(requestPayload, "Payload is null");
            checkNotNull(options, "Options cannot be null");
            final UPriority priority = checkPriority(options);
            final int timeout = checkArgumentPositive(options.getTtl(), "Timeout is not positive");
            final UAttributesBuilder builder = UAttributesBuilder.request(mResponseUri, methodUri, priority, timeout);
            if (options.hasToken()) {
                builder.withToken(options.getToken());
            }
            final UMessage requestMessage = UMessage.newBuilder()
                    .setPayload(requestPayload)
                    .setAttributes(builder.build())
                    .build();
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

    private static @NonNull UPriority checkPriority(@NonNull CallOptions options) {
        final UPriority priority = options.getPriority();
        checkArgument(priority.getNumber() >= UPriority.UPRIORITY_CS4.getNumber(),
                "Priority must be equal or higher than " + UPriority.UPRIORITY_CS4);
        return priority;
    }

    private @NonNull CompletableFuture<UMessage> buildClientResponseFuture(@NonNull UMessage requestMessage) {
        final CompletableFuture<UMessage> responseFuture = new CompletableFuture<UMessage>()
                .orTimeout(requestMessage.getAttributes().getTtl(), TimeUnit.MILLISECONDS);
        responseFuture.whenComplete((responseMessage, exception) ->
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
            case UMESSAGE_TYPE_PUBLISH, UMESSAGE_TYPE_NOTIFICATION -> handleGenericMessage(message);
            case UMESSAGE_TYPE_REQUEST -> handleRequestMessage(message);
            case UMESSAGE_TYPE_RESPONSE -> handleResponseMessage(message);
            default -> Log.w(mTag, join(Key.EVENT, MESSAGE_DROPPED, Key.MESSAGE, stringify(message), Key.REASON, "Unknown type"));
        }
    }

    private void handleGenericMessage(@NonNull UMessage message) {
        if (message.getAttributes().hasSink()) {
            final UEntity entity = message.getAttributes().getSink().getEntity();
            if (!entity.equals(mUri.getEntity())) {
                Log.w(mTag, join(Key.EVENT, MESSAGE_DROPPED, Key.MESSAGE, stringify(message), Key.REASON, "Wrong sink"));
                return;
            }
        }
        mCallbackExecutor.execute(() -> {
            final UUri topic = message.getAttributes().getSource();
            final Set<UListener> listeners;
            synchronized (mRegistrationLock) {
                listeners = new ArraySet<>(mGenericListeners.get(topic));
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
            final UListener listener;
            synchronized (mRegistrationLock) {
                listener = mRequestListeners.get(methodUri);
                if (listener == null) {
                    Log.w(mTag, join(Key.EVENT, MESSAGE_DROPPED, Key.MESSAGE, stringify(requestMessage), Key.REASON, "No listener"));
                    return;
                }
            }
            listener.onReceive(requestMessage);
        });
    }

    private void handleResponseMessage(@NonNull UMessage responseMessage) {
        final UAttributes responseAttributes = responseMessage.getAttributes();
        final CompletableFuture<UMessage> responseFuture = mRequests.remove(responseAttributes.getReqid());
        if (responseFuture == null) {
            return;
        }
        if (responseAttributes.hasCommstatus()) {
            final UCode code = responseAttributes.getCommstatus();
            if (code != UCode.OK) {
                responseFuture.completeExceptionally(new UStatusException(code, "Communication error [" + code + "]"));
                return;
            }
        }
        responseFuture.complete(responseMessage);
    }
}
