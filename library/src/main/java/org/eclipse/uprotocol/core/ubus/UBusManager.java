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
package org.eclipse.uprotocol.core.ubus;

import static android.content.Context.BIND_AUTO_CREATE;

import static org.eclipse.uprotocol.UPClient.TAG_GROUP;
import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkNotNull;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkStatusOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.isOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.status;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;

import static java.util.Objects.requireNonNull;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.client.R;
import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;
import org.eclipse.uprotocol.v1.internal.ParcelableUEntity;
import org.eclipse.uprotocol.v1.internal.ParcelableUMessage;
import org.eclipse.uprotocol.v1.internal.ParcelableUUri;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * The manager that provides access to uBus.
 */
public final class UBusManager {
    public static final String ACTION_BIND_UBUS = "uprotocol.action.BIND_UBUS";

    private static final int REBIND_BACKOFF_EXPONENT_MAX = 5;
    private static final int REBIND_BACKOFF_BASE = 2;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            STATE_DISCONNECTED,
            STATE_CONNECTING,
            STATE_CONNECTED,
    })
    @Target({ElementType.TYPE_USE})
    private @interface StateTypeEnum {}

    private final Context mContext;
    private final UEntity mEntity;
    private final IBinder mClientToken = new Binder();
    private final ConnectionCallback mConnectionCallback;
    private final UListener mListener;
    private final ScheduledExecutorService mConnectionExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Object mConnectionLock = new Object();
    private final String mServiceConfig;
    private final String mTag;
    private boolean mDebugLoggable;
    private boolean mVerboseLoggable;

    @GuardedBy("mConnectionLock")
    @StateTypeEnum
    private int mConnectionState = STATE_DISCONNECTED;
    @GuardedBy("mConnectionLock")
    private CompletableFuture<UStatus> mConnectionFuture;
    @GuardedBy("mConnectionLock")
    private IUBus mService;
    @GuardedBy("mConnectionLock")
    private boolean mServiceBound;
    @GuardedBy("mConnectionLock")
    private int mRebindBackoffExponent;

    private final ServiceConnection mServiceConnectionCallback = new ServiceConnection()  {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mConnectionExecutor.execute(() -> {
                try {
                    final IUBus newService = IUBus.Stub.asInterface(service);
                    synchronized (mConnectionLock) {
                        if (!mServiceBound) {
                            if (mDebugLoggable) {
                                Log.d(mTag, join(Key.MESSAGE, "Service connection was cancelled"));
                            }
                            return;
                        }
                        if (mService != null && mService.asBinder().equals(newService.asBinder())) {
                            if (mDebugLoggable) {
                                Log.d(mTag, join(Key.MESSAGE, "Service is already connected"));
                            }
                            return;
                        }
                    }
                    checkStatusOk(registerClient(newService));
                    synchronized (mConnectionLock) {
                        mService = newService;
                        setConnectionStateLocked(STATE_CONNECTED);
                        completeConnectionLocked(STATUS_OK);
                    }
                    mConnectionCallback.onConnected();
                } catch (Exception exception) {
                    Log.e(mTag, join(Key.EVENT, "Service connection failed", Key.REASON, exception.getMessage()));
                    synchronized (mConnectionLock) {
                        unbindServiceLocked();
                        handleServiceDisconnectLocked();
                        completeConnectionLocked(toStatus(exception));
                    }
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mConnectionExecutor.execute(() -> {
                synchronized (mConnectionLock) {
                    if (mConnectionState == STATE_DISCONNECTED) {
                        if (mDebugLoggable) {
                            Log.d(mTag, join(Key.MESSAGE, "Service is already disconnected"));
                        }
                        return;
                    }
                    Log.w(mTag, join(Key.EVENT, "Service unexpectedly disconnected"));
                    handleServiceDisconnectLocked();
                }
                mConnectionCallback.onConnectionInterrupted();
            });
        }

        @Override
        public void onBindingDied(ComponentName name) {
            final long delay = calculateRebindDelaySeconds();
            Log.w(mTag, join(Key.EVENT, "Service binder died", Key.MESSAGE, "Rebind in " + delay + " second(s)..."));
            mConnectionExecutor.schedule(() -> {
                synchronized (mConnectionLock) {
                    if (!mServiceBound) {
                        if (mDebugLoggable) {
                            Log.d(mTag, join(Key.MESSAGE, "Service connection was cancelled"));
                        }
                        return;
                    }
                    if (mConnectionState == STATE_CONNECTED) {
                        if (mDebugLoggable) {
                            Log.d(mTag, join(Key.MESSAGE, "Service is already connected"));
                        }
                        return;
                    }
                    unbindServiceLocked();
                    final UStatus status = bindServiceLocked();
                    if (!isOk(status)) {
                        handleServiceDisconnectLocked();
                        completeConnectionLocked(status);
                    }
                }
            }, delay, TimeUnit.SECONDS);
        }

        @Override
        public void onNullBinding(ComponentName name) {
            mConnectionExecutor.execute(() -> {
                synchronized (mConnectionLock) {
                    Log.e(mTag, join(Key.EVENT, "Null binding to service", Key.NAME, name.getClassName()));
                    unbindServiceLocked();
                    handleServiceDisconnectLocked();
                    completeConnectionLocked(buildStatus(UCode.NOT_FOUND, "Service is disabled"));
                }
                mConnectionCallback.onDisconnected();
            });
        }
    };

    private final IUListener.Stub mServiceListener = new IUListener.Stub() {
        public void onReceive(ParcelableUMessage data) {
           mListener.onReceive(data.getWrapped());
        }
    };

    public UBusManager(@NonNull Context context, @NonNull UEntity entity, @NonNull ConnectionCallback callback,
            @NonNull UListener listener) {
        mContext = requireNonNull(context);
        mEntity = requireNonNull(entity);
        mConnectionCallback = requireNonNull(callback);
        mListener = requireNonNull(listener);
        mServiceConfig = mContext.getString(R.string.config_UBusService);
        mTag = tag(entity.getName(), TAG_GROUP);
        mDebugLoggable = Log.isLoggable(mTag, Log.DEBUG);
        mVerboseLoggable = Log.isLoggable(mTag, Log.VERBOSE);
    }

    @SuppressLint("SwitchIntDef")
    public @NonNull CompletableFuture<UStatus> connect() {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (mConnectionLock) {
                switch (mConnectionState) {
                    case STATE_DISCONNECTED -> {
                        final UStatus status = bindServiceLocked();
                        if (isOk(status)) {
                            setConnectionStateLocked(STATE_CONNECTING);
                            mConnectionFuture = new CompletableFuture<>();
                            return mConnectionFuture;
                        } else {
                            handleServiceDisconnectLocked();
                            mConnectionFuture = null;
                            return CompletableFuture.completedFuture(status);
                        }
                    }
                    case STATE_CONNECTED -> {
                        return CompletableFuture.completedFuture(STATUS_OK);
                    }
                    default /* STATE_CONNECTING */ -> {
                        return mConnectionFuture;
                    }
                }
            }
        }, mConnectionExecutor).thenCompose(Function.identity());
    }

    public @NonNull CompletableFuture<UStatus> disconnect() {
        return CompletableFuture.supplyAsync(() -> {
            final IUBus service;
            final int oldState;
            synchronized (mConnectionLock) {
                service = mService;
                oldState = mConnectionState;
                unbindServiceLocked();
                handleServiceDisconnectLocked();
                completeConnectionLocked(buildStatus(UCode.CANCELLED, "Service connection is cancelled"));
            }
            if (service != null) {
                unregisterClient(service);
            }
            if (oldState != STATE_DISCONNECTED) {
                mConnectionCallback.onDisconnected();
            }
            return STATUS_OK;
        }, mConnectionExecutor);
    }

    public boolean isDisconnected() {
        synchronized (mConnectionLock) {
            return mConnectionState == STATE_DISCONNECTED;
        }
    }

    public boolean isConnecting() {
        synchronized (mConnectionLock) {
            return mConnectionState == STATE_CONNECTING;
        }
    }

    public boolean isConnected() {
        synchronized (mConnectionLock) {
            return mConnectionState == STATE_CONNECTED;
        }
    }

    private @NonNull IUBus getServiceOrThrow() throws UStatusException {
        synchronized (mConnectionLock) {
            return checkNotNull(mService, UCode.UNAVAILABLE, "Service is not connected");
        }
    }

    @GuardedBy("mConnectionLock")
    private void handleServiceDisconnectLocked() {
        if (mConnectionState != STATE_DISCONNECTED) {
            setConnectionStateLocked(STATE_DISCONNECTED);
            mService = null;
            mRebindBackoffExponent = 0;
        }
    }

    @GuardedBy("mConnectionLock")
    private void setConnectionStateLocked(@StateTypeEnum int state) {
        if (mConnectionState != state) {
            if (mVerboseLoggable) {
                Log.v(mTag, join(Key.MESSAGE, "Connection state: " + mConnectionState + " -> " + state));
            }
            mConnectionState = state;
        }
    }

    @GuardedBy("mConnectionLock")
    private void completeConnectionLocked(@NonNull UStatus status) {
        if (mConnectionFuture != null) {
            mConnectionFuture.complete(status);
            mConnectionFuture = null;
        }
    }

    private @NonNull Intent buildServiceIntent() {
        final Intent intent = new Intent(ACTION_BIND_UBUS);
        final ComponentName component = ComponentName.unflattenFromString(mServiceConfig);
        if (component != null) {
            intent.setComponent(component);
        } else if (!mServiceConfig.isEmpty()) {
            intent.setPackage(mServiceConfig);
        }
        return intent;
    }

    @GuardedBy("mConnectionLock")
    private @NonNull UStatus bindServiceLocked() {
        UStatus status;
        try {
            mServiceBound = mContext.bindService(buildServiceIntent(), mServiceConnectionCallback, BIND_AUTO_CREATE);
            status = mServiceBound ? STATUS_OK : buildStatus(UCode.NOT_FOUND, "Service is not found");
        } catch (Exception e) {
            mServiceBound = false;
            status = toStatus(e);
        }
        if (isVerboseLoggable(status)) {
            Log.println(verboseOrError(status), mTag, status("bindService", status));
        }
        return status;
    }

    @GuardedBy("mConnectionLock")
    private void unbindServiceLocked() {
        if (mServiceBound) {
            mContext.unbindService(mServiceConnectionCallback);
            mServiceBound = false;
            if (mVerboseLoggable) {
                Log.v(mTag, status("unbindService", STATUS_OK));
            }
        }
    }

    @VisibleForTesting
    long calculateRebindDelaySeconds() {
        synchronized (mConnectionLock) {
            final int exponent = mRebindBackoffExponent;
            if (mRebindBackoffExponent < REBIND_BACKOFF_EXPONENT_MAX) {
                mRebindBackoffExponent++;
            }
            return (long) Math.pow(REBIND_BACKOFF_BASE, exponent);
        }
    }

    @SuppressWarnings("java:S3398")
    private @NonNull UStatus registerClient(@NonNull IUBus service) {
        UStatus status;
        try {
            status = service.registerClient(mContext.getPackageName(),
                    new ParcelableUEntity(mEntity), mClientToken, 0, mServiceListener).getWrapped();
        } catch (Exception e) {
            status = toStatus(e);
        }
        if (isDebugLoggable(status)) {
            Log.println(debugOrError(status), mTag, status("registerClient", status));
        }
        return status;
    }

    @SuppressWarnings("UnusedReturnValue")
    private @NonNull UStatus unregisterClient(@NonNull IUBus service) {
        UStatus status;
        try {
            status = service.unregisterClient(mClientToken).getWrapped();
        } catch (Exception e) {
            status = toStatus(e);
        }
        if (isDebugLoggable(status)) {
            Log.println(debugOrError(status), mTag, status("unregisterClient", status));
        }
        return status;
    }

    public @NonNull UStatus send(@NonNull UMessage message) {
        UStatus status;
        try {
            checkNotNull(message, "Message is null");
            status = getServiceOrThrow().send(new ParcelableUMessage(message), mClientToken).getWrapped();
        } catch (Exception e) {
            status = toStatus(e);
        }
        if (isVerboseLoggable(status)) {
            Log.println(verboseOrError(status), mTag, status( "send", status, Key.MESSAGE, stringify(message)));
        }
        return status;
    }

    public @NonNull UStatus enableDispatching(@NonNull UUri uri) {
        UStatus status;
        try {
            status = getServiceOrThrow().enableDispatching(new ParcelableUUri(uri), null, mClientToken).getWrapped();
        } catch (Exception e) {
            status = toStatus(e);
        }
        if (isDebugLoggable(status)) {
            Log.println(debugOrError(status), mTag, status("enableDispatching", status, Key.URI, stringify(uri)));
        }
        return status;
    }

    public @NonNull UStatus disableDispatching(@NonNull UUri uri) {
        UStatus status;
        try {
            status = getServiceOrThrow().disableDispatching(new ParcelableUUri(uri), null, mClientToken).getWrapped();
        } catch (Exception e) {
            status = toStatus(e);
        }
        if (isDebugLoggable(status)) {
            Log.println(debugOrError(status), mTag, status("disableDispatching", status, Key.URI, stringify(uri)));
        }
        return status;
    }

    public void disableDispatchingQuietly(@NonNull UUri uri) {
        disableDispatching(uri);
    }

    public @Nullable UMessage getLastMessage(@NonNull UUri topic) {
        try {
            final ParcelableUMessage[] bundle = getServiceOrThrow()
                    .pull(new ParcelableUUri(topic), 1, null, mClientToken);
            return (bundle != null && bundle.length > 0) ? bundle[0].getWrapped() : null;
        } catch (Exception e) {
            Log.e(mTag, status("getLastMessage", toStatus(e), Key.URI, stringify(topic)));
            return null;
        }
    }

    private boolean isDebugLoggable(@NonNull UStatus status) {
        return mDebugLoggable || !isOk(status);
    }

    private boolean isVerboseLoggable(@NonNull UStatus status) {
        return mVerboseLoggable || !isOk(status);
    }

    @VisibleForTesting
    void setLoggable(int level) {
        mDebugLoggable = level <= Log.DEBUG;
        mVerboseLoggable = level <= Log.VERBOSE;
    }

    private static int debugOrError(@NonNull UStatus status) {
        return isOk(status) ? Log.DEBUG : Log.ERROR;
    }

    private static int verboseOrError(@NonNull UStatus status) {
        return isOk(status) ? Log.VERBOSE : Log.ERROR;
    }
}
