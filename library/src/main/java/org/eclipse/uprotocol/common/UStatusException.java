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
package org.eclipse.uprotocol.common;

import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UStatus;

/**
 * The unchecked exception which carries uProtocol error model.
 */
public class UStatusException extends RuntimeException {
    private final UStatus mStatus;

    /**
     * Constructs an instance.
     *
     * @param code    An error {@link UCode}.
     * @param message An error message.
     */
    public UStatusException(UCode code, String message) {
        this(buildStatus(code, message), null);
    }

    /**
     * Constructs an instance.
     *
     * @param code    An error {@link UCode}.
     * @param message An error message.
     * @param cause   An exception that caused this one.
     */
    public UStatusException(UCode code, String message, Throwable cause) {
        this(buildStatus(code, message), cause);
    }

    /**
     * Constructs an instance.
     *
     * @param status An error {@link UStatus}.
     */
    public UStatusException(UStatus status) {
        this(status, null);
    }

    /**
     * Constructs an instance.
     *
     * @param status An error {@link UStatus}.
     * @param cause  An exception that caused this one.
     */
    public UStatusException(UStatus status, Throwable cause) {
        super((status != null) ? status.getMessage() : "", cause);
        mStatus = (status != null) ? status : buildStatus(UCode.UNKNOWN);
    }

    /**
     * Get the error status.
     * @return The error {@link UStatus}.
     */
    public @NonNull UStatus getStatus() {
        return mStatus;
    }

    /**
     * Get the error code.
     * @return The error {@link UCode}.
     */
    public @NonNull UCode getCode() {
        return mStatus.getCode();
    }

    /**
     * Get the error message.
     * @return The error message.
     */
    @Override
    public @Nullable String getMessage() {
        return mStatus.getMessage();
    }
}
