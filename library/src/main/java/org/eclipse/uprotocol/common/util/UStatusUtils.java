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
package org.eclipse.uprotocol.common.util;

import android.os.RemoteException;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.InvalidProtocolBufferException;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UStatus;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface UStatusUtils {
    UStatus STATUS_OK = buildStatus(UCode.OK);

    static boolean isOk(@Nullable UStatus status) {
        return status != null && status.getCodeValue() == UCode.OK_VALUE;
    }

    static boolean hasCode(@Nullable UStatus status, int code) {
        return status != null && status.getCodeValue() == code;
    }

    static boolean hasCode(@Nullable UStatus status, @NonNull UCode code) {
        return status != null && status.getCodeValue() == code.getNumber();
    }

    static @NonNull UCode getCode(@Nullable UStatus status, @NonNull UCode defaultCode) {
        return (status != null) ? status.getCode() : defaultCode;
    }

    static @NonNull UCode getCode(@Nullable UStatus status) {
        return getCode(status, UCode.UNKNOWN);
    }

    static @NonNull UCode toCode(int value) {
        final UCode code = UCode.forNumber(value);
        return (code != null) ? code : UCode.UNKNOWN;
    }

    static @NonNull UStatus.Builder newStatusBuilder(@NonNull UCode code) {
        return UStatus.newBuilder().setCode(code);
    }

    static @NonNull UStatus.Builder newStatusBuilder(@NonNull UCode code, @Nullable String message) {
        UStatus.Builder builder = newStatusBuilder(code);
        if (message != null) {
            builder.setMessage(message);
        }
        return builder;
    }

    static @NonNull UStatus buildStatus(@NonNull UCode code) {
        return newStatusBuilder(code).build();
    }

    static @NonNull UStatus buildStatus(@NonNull UCode code, @Nullable String message) {
        return newStatusBuilder(code, message).build();
    }

    static @NonNull UStatus toStatus(@NonNull Throwable exception) {
        if (exception instanceof UStatusException statusException) {
            return statusException.getStatus();
        } else if (exception instanceof CompletionException || (exception instanceof ExecutionException)) {
            final Throwable cause = exception.getCause();
            if (cause instanceof UStatusException statusException) {
                return statusException.getStatus();
            } else if (cause != null) {
                return buildStatus(toCode(cause), cause.getMessage());
            }
        }
        return buildStatus(toCode(exception), exception.getMessage());
    }

    private static @NonNull UCode toCode(@NonNull Throwable exception) {
        if (exception instanceof SecurityException) {
            return UCode.PERMISSION_DENIED;
        } else if (exception instanceof InvalidProtocolBufferException) {
            return UCode.INVALID_ARGUMENT;
        } else if (exception instanceof IllegalArgumentException) {
            return UCode.INVALID_ARGUMENT;
        } else if (exception instanceof NullPointerException) {
            return UCode.INVALID_ARGUMENT;
        } else if (exception instanceof CancellationException) {
            return UCode.CANCELLED;
        } else if (exception instanceof IllegalStateException) {
            return UCode.UNAVAILABLE;
        } else if (exception instanceof RemoteException) {
            return UCode.UNAVAILABLE;
        } else if (exception instanceof UnsupportedOperationException) {
            return UCode.UNIMPLEMENTED;
        } else if (exception instanceof InterruptedException) {
            return UCode.CANCELLED;
        } else if (exception instanceof TimeoutException) {
            return UCode.DEADLINE_EXCEEDED;
        } else {
            return UCode.UNKNOWN;
        }
    }

    static void checkStatusOk(@NonNull UStatus status) {
        if (!isOk(status)) {
            throw new UStatusException(status);
        }
    }

    static void checkArgument(boolean expression, @Nullable String errorMessage) {
        if (!expression) {
            throw new UStatusException(UCode.INVALID_ARGUMENT, errorMessage);
        }
    }

    static void checkArgument(boolean expression, @NonNull UCode errorCode, @Nullable String errorMessage) {
        if (!expression) {
            throw new UStatusException(errorCode, errorMessage);
        }
    }

    static int checkArgumentPositive(int value, @Nullable String errorMessage) {
        if (value <= 0) {
            throw new UStatusException(UCode.INVALID_ARGUMENT, errorMessage);
        }
        return value;
    }

    static int checkArgumentPositive(int value, @NonNull UCode errorCode, @Nullable String errorMessage) {
        if (value <= 0) {
            throw new UStatusException(errorCode, errorMessage);
        }
        return value;
    }

    static int checkArgumentNonNegative(int value, @Nullable String errorMessage) {
        if (value < 0) {
            throw new UStatusException(UCode.INVALID_ARGUMENT, errorMessage);
        }
        return value;
    }

    static int checkArgumentNonNegative(int value, @NonNull UCode errorCode, @Nullable String errorMessage) {
        if (value < 0) {
            throw new UStatusException(errorCode, errorMessage);
        }
        return value;
    }

    static @NonNull <T extends CharSequence> T checkStringNotEmpty(T string, @Nullable String errorMessage) {
        if (TextUtils.isEmpty(string)) {
            throw new UStatusException(UCode.INVALID_ARGUMENT, errorMessage);
        }
        return string;
    }

    static @NonNull <T extends CharSequence> T checkStringNotEmpty(T string, @NonNull UCode errorCode,
            @Nullable String errorMessage) {
        if (TextUtils.isEmpty(string)) {
            throw new UStatusException(errorCode, errorMessage);
        }
        return string;
    }

    static @NonNull <T extends CharSequence> T checkStringEquals(T string1, @NonNull T string2,
            @NonNull UCode errorCode, @Nullable String errorMessage) {
        if (!TextUtils.equals(string1, string2)) {
            throw new UStatusException(errorCode, errorMessage);
        }
        return string1;
    }

    static @NonNull <T extends CharSequence> T checkStringEquals(T string1, T string2,
            @Nullable String errorMessage) {
        if (!TextUtils.equals(string1, string2)) {
            throw new UStatusException(UCode.INVALID_ARGUMENT, errorMessage);
        }
        return string1;
    }

    static @NonNull <T> T checkNotNull(@Nullable T reference, @Nullable String errorMessage) {
        if (reference == null) {
            throw new UStatusException(UCode.INVALID_ARGUMENT, errorMessage);
        }
        return reference;
    }

    static @NonNull <T> T checkNotNull(@Nullable T reference, @NonNull UCode errorCode,
            @Nullable String errorMessage) {
        if (reference == null) {
            throw new UStatusException(errorCode, errorMessage);
        }
        return reference;
    }

    static void checkState(boolean expression, @Nullable String errorMessage) {
        if (!expression) {
            throw new UStatusException(UCode.FAILED_PRECONDITION, errorMessage);
        }
    }

    static void checkState(boolean expression, @NonNull UCode errorCode, @Nullable String errorMessage) {
        if (!expression) {
            throw new UStatusException(errorCode, errorMessage);
        }
    }
}
