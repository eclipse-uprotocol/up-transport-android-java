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
package org.eclipse.uprotocol.transport;

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkNotNull;
import static org.eclipse.uprotocol.common.util.UStatusUtils.isOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.uri.factory.UriFactory.WILDCARD_ENTITY_ID;
import static org.eclipse.uprotocol.uri.factory.UriFactory.WILDCARD_ENTITY_VERSION;
import static org.eclipse.uprotocol.uri.validator.UriValidator.matchesEntity;

import static java.util.Optional.ofNullable;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.ubus.ConnectionCallback;
import org.eclipse.uprotocol.core.ubus.UBusManager;
import org.eclipse.uprotocol.internal.HandlerExecutor;
import org.eclipse.uprotocol.uri.validator.UriFilter;
import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * UTransport implementation based on Android Binder.
 */
public class UTransportAndroid implements UTransport {
    /**
     * The logging tag used by this class and all sub-components.
     */
    public static final String TAG = "uTransport";

    /**
     * The permission necessary to connect to the uBus.
     */
    public static final String PERMISSION_ACCESS_UBUS = "uprotocol.permission.ACCESS_UBUS";

    /**
     * The name of the <code>meta-data</code> element that must be present on an
     * <code>application</code> or <code>service</code> element in a manifest to specify
     * the entity id.
     */
    public static final String META_DATA_ENTITY_ID = "uprotocol.entity.id";

    /**
     * The name of the <code>meta-data</code> element that must be present on an
     * <code>application</code> or <code>service</code> element in a manifest to specify
     * the entity major version.
     */
    public static final String META_DATA_ENTITY_VERSION = "uprotocol.entity.version";

    private final UUri mSource;
    private final UBusManager mUBusManager;
    private final Executor mCallbackExecutor;

    private final Object mRegistrationLock = new Object();
    @GuardedBy("mRegistrationLock")
    private final Map<UriFilter, Set<UListener>> mListeners = new HashMap<>();
    @GuardedBy("mRegistrationLock")
    private boolean mRegistrationExpired;

    private final String mTag;
    private boolean mVerboseLoggable;

    private final ConnectionCallback mConnectionCallback = new ConnectionCallback() {
        @Override
        public void onConnected() {
            synchronized (mRegistrationLock) {
                if (mRegistrationExpired) {
                    mListeners.keySet().forEach(mUBusManager::enableDispatching);
                    mRegistrationExpired = false;
                }
            }
        }

        @Override
        public void onDisconnected() {
            synchronized (mRegistrationLock) {
                mListeners.clear();
                mRegistrationExpired = false;
            }
        }

        @Override
        public void onConnectionInterrupted() {
            synchronized (mRegistrationLock) {
                mRegistrationExpired = true;
            }
        }
    };

    private final UListener mListener = this::handleMessage;

    @VisibleForTesting
    UTransportAndroid(@NonNull Context context, UUri source, UBusManager manager, Executor executor) {
        checkNonNullContext(context);
        mSource = checkSource(getPackageInfo(context), source);
        mUBusManager = ofNullable(manager).orElse(new UBusManager(context, mSource, mConnectionCallback, mListener));
        mCallbackExecutor = ofNullable(executor).orElse(context.getMainExecutor());

        mTag = tag(TAG, Integer.toHexString(mSource.getUeId()));
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

    private static @NonNull UUri checkSource(@NonNull PackageInfo packageInfo, UUri source) {
        return Stream.concat(Stream.of(packageInfo.applicationInfo),
                        (packageInfo.services != null) ? Stream.of(packageInfo.services) : Stream.empty())
                .filter(Objects::nonNull)
                .map(info -> {
                    final UUri foundSource = getSource(info);
                    if (source != null && foundSource != null) {
                        return matchesEntity(source, foundSource) ? foundSource : null;
                    } else {
                        return foundSource;
                    }
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new SecurityException("Missing or not matching '" +
                        META_DATA_ENTITY_ID  + "' or '" + META_DATA_ENTITY_VERSION + "' meta-data in manifest"));
    }

    private static UUri getSource(@NonNull PackageItemInfo info) {
        if (info.metaData != null) {
            final int id = info.metaData.getInt(META_DATA_ENTITY_ID);
            final int version = info.metaData.getInt(META_DATA_ENTITY_VERSION);
            if (id > 0 && id != WILDCARD_ENTITY_ID && version > 0 && version != WILDCARD_ENTITY_VERSION) {
                final UUri.Builder builder = UUri.newBuilder()
                        .setUeId(id)
                        .setUeVersionMajor(version);
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
     * @return A {@link UTransportAndroid} instance.
     * @throws SecurityException If the caller does not have {@link #META_DATA_ENTITY_ID} and
     *         {@link #META_DATA_ENTITY_VERSION} <code>meta-data</code> elements declared in the manifest.
     */
    public static @NonNull UTransportAndroid create(@NonNull Context context, Handler handler) {
        return new UTransportAndroid(context, null, null, new HandlerExecutor(handler));
    }

    /**
     * Create an instance for a specified source.
     *
     * @param context  An application {@link Context}. This should not be <code>null</code>. If you are passing
     *                 {@link ContextWrapper}, make sure that its base Context is non-null as well.
     *                 Otherwise it will throw {@link NullPointerException}.
     * @param source   A {@link UUri} containing entity id and major version, or <code>null</code> to use the
     *                 first found declaration under <code>application</code> or <code>service</code> element
     *                 in a manifest.
     * @param handler  A {@link Handler} on which callbacks should execute, or <code>null</code> to execute on
     *                 the application's main thread.
     * @return A {@link UTransportAndroid} instance.
     * @throws SecurityException If the caller does not have {@link #META_DATA_ENTITY_ID} and
     *         {@link #META_DATA_ENTITY_VERSION} <code>meta-data</code> elements declared in the manifest.
     */
    public static @NonNull UTransportAndroid create(@NonNull Context context, UUri source, Handler handler) {
        return new UTransportAndroid(context, source, null, new HandlerExecutor(handler));
    }

    /**
     * Create an instance.
     *
     * @param context  An application {@link Context}. This should not be <code>null</code>. If you are passing
     *                 {@link ContextWrapper}, make sure that its base Context is non-null as well.
     *                 Otherwise it will throw {@link NullPointerException}.
     * @param executor An {@link Executor} on which callbacks should execute, or <code>null</code> to execute on
     *                 the application's main thread.
     * @return A {@link UTransportAndroid} instance.
     * @throws SecurityException If the caller does not have {@link #META_DATA_ENTITY_ID} and
     *         {@link #META_DATA_ENTITY_VERSION} <code>meta-data</code> elements declared in the manifest.
     */
    public static @NonNull UTransportAndroid create(@NonNull Context context, Executor executor) {
        return new UTransportAndroid(context, null, null, executor);
    }

    /**
     * Create an instance for a specified source.
     *
     * @param context  An application {@link Context}. This should not be <code>null</code>. If you are passing
     *                 {@link ContextWrapper}, make sure that its base Context is non-null as well.
     *                 Otherwise it will throw {@link NullPointerException}.
     * @param source   A {@link UUri} containing entity id and major version, or <code>null</code> to use the
     *                 first found declaration under <code>application</code> or <code>service</code> element
     *                 in a manifest.
     * @param executor An {@link Executor} on which callbacks should execute, or <code>null</code> to execute on
     *                 the application's main thread.
     * @return A {@link UTransportAndroid} instance.
     * @throws SecurityException If the caller does not have {@link #META_DATA_ENTITY_ID} and
     *         {@link #META_DATA_ENTITY_VERSION} <code>meta-data</code> elements declared in the manifest.
     */
    public static @NonNull UTransportAndroid create(@NonNull Context context, UUri source, Executor executor) {
        return new UTransportAndroid(context, source, null, executor);
    }

    /**
     * Check whether the connection to the uBus is opened or not. This will return <code>false</code>
     * if the connection is still in progress.
     *
     * @return <code>true</code> if is is connected.
     */
    public boolean isOpened() {
        return mUBusManager.isConnected();
    }

    /**
     * Open the connection to the uBus.
     *
     * <p>Requires {@link #PERMISSION_ACCESS_UBUS} permission to access this API.
     *
     * <p>An instance connected with this method should be disconnected from the uBus by calling
     * {@link #close()} before the passed {@link Context} is released.
     *
     * @return A {@link CompletionStage<UStatus>} used by a caller to receive the connection status.
     */
    @Override
    public @NonNull CompletionStage<UStatus> open() {
        return mUBusManager.connect();
    }

    /**
     * Close the connection to the uBus.
     *
     * <p>All previously registered listeners will be automatically unregistered.
     */
    @Override
    public void close() {
        mUBusManager.disconnect().toCompletableFuture().join();
    }

    /**
     * Get the source address of the entity.
     *
     * @return {@link UUri} representing the source address.
     */
    @Override
    public UUri getSource() {
        return mSource;
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
     * Send a message.
     *
     * @param message A {@link UMessage} to be sent.
     * @return A {@link CompletionStage<UStatus>} which provides a result code and other details upon completion.
     */
    @Override
    public CompletionStage<UStatus> send(UMessage message) {
        return CompletableFuture.supplyAsync(() -> mUBusManager.send(message));
    }

    /**
     * Register a listener to receive messages.
     *
     * @param sourceFilter The source address filter.
     * @param sinkFilter   The sink address filter.
     * @param listener     The {@link UListener} that will be executed when the message matching filters is received.
     * @return A {@link CompletionStage<UStatus>} which provides a result code and other details upon completion.
     */
    @Override
    public CompletionStage<UStatus> registerListener(UUri sourceFilter, UUri sinkFilter, UListener listener) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                checkNotNull(listener, "Listener is null");
                final UriFilter filter = new UriFilter(sourceFilter, sinkFilter);
                synchronized (mRegistrationLock) {
                    final Set<UListener> listeners = ofNullable(mListeners.get(filter)).orElse(new HashSet<>());
                    if (listeners.isEmpty()) {
                        final UStatus status = mUBusManager.enableDispatching(filter);
                        if (!isOk(status)) {
                            return status;
                        }
                        mListeners.put(filter, listeners);
                    }
                    listeners.add(listener);
                    return STATUS_OK;
                }
            } catch (Exception e) {
                return toStatus(e);
            }
        });
    }

    /**
     * Unregister a listener.
     *
     * @param sourceFilter The source address filter.
     * @param sinkFilter   The sink address filter.
     * @param listener     The {@link UListener} to be unregistered.
     * @return A {@link CompletionStage<UStatus>} which provides a result code and other details upon completion.
     */
    @Override
    public CompletionStage<UStatus> unregisterListener(UUri sourceFilter, UUri sinkFilter, UListener listener) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                checkNotNull(listener, "Listener is null");
                final UriFilter filter = new UriFilter(sourceFilter, sinkFilter);
                synchronized (mRegistrationLock) {
                    final Set<UListener> listeners = mListeners.get(filter);
                    if (listeners != null && listeners.contains(listener)) {
                        listeners.remove(listener);
                        if (listeners.isEmpty()) {
                            mUBusManager.disableDispatchingQuietly(filter);
                            mListeners.remove(filter);
                        }
                    }
                }
                return STATUS_OK;
            } catch (Exception e) {
                return toStatus(e);
            }
        });
    }

    private void handleMessage(@NonNull UMessage message) {
        if (mVerboseLoggable) {
            Log.v(mTag, join(Key.EVENT, "Message received", Key.MESSAGE, stringify(message)));
        }
        mCallbackExecutor.execute(() -> {
            final Set<UListener> matchedListeners = new HashSet<>();
            final UAttributes attributes = message.getAttributes();
            synchronized (mRegistrationLock) {
                mListeners.forEach((filter, listeners) -> {
                    if (filter.matches(attributes)) {
                        matchedListeners.addAll(listeners);
                    }
                });
            }
            matchedListeners.forEach(listener -> {
                try {
                    listener.onReceive(message);
                } catch (Exception e) {
                    Log.e(mTag, join(Key.FAILURE, "Listener failed", Key.REASON, e.getMessage()));
                }
            });
        });
    }
}
