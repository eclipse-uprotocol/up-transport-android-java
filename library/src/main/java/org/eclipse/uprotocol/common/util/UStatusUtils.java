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

/**
 * Utility methods for building, converting to uProtocol error model, and validating arguments and states.
 */
public interface UStatusUtils {
    UStatus STATUS_OK = buildStatus(UCode.OK);

    /**
     * Checks whether a status is successful.
     *
     * @param status A {@link UStatus} with an error code.
     * @return <code>true</code> if it contains {@link UCode#OK}.
     */
    static boolean isOk(@Nullable UStatus status) {
        return status != null && status.getCodeValue() == UCode.OK_VALUE;
    }

    /**
     * Checks whether a status contains a given code.
     *
     * @param status A {@link UStatus} with an error code.
     * @param code   An <code>int</code> value of a {@link UCode} to check.
     * @return <code>true</code> if it contains the same code.
     */
    static boolean hasCode(@Nullable UStatus status, int code) {
        return status != null && status.getCodeValue() == code;
    }

    /**
     * Checks whether a status contains a given code.
     *
     * @param status A {@link UStatus} with an error code.
     * @param code   A {@link UCode} to check.
     * @return <code>true</code> if it contains the same code.
     */
    static boolean hasCode(@Nullable UStatus status, @NonNull UCode code) {
        return status != null && status.getCodeValue() == code.getNumber();
    }

    /**
     * Get a code from a status.
     *
     * @param status      A {@link UStatus} with an error code.
     * @param defaultCode A default {@link UCode}.
     * @return A {@link UCode} from the given <code>status</code> if is not <code>null</code>,
     *         otherwise <code>defaultCode</code>.
     */
    static @NonNull UCode getCode(@Nullable UStatus status, @NonNull UCode defaultCode) {
        return (status != null) ? status.getCode() : defaultCode;
    }

    /**
     * Get a code from a status.
     *
     * @param status A {@link UStatus} with an error code.
     * @return A {@link UCode} from the given <code>status</code> if is not <code>null</code>,
     *         otherwise {@link UCode#UNKNOWN}.
     */
    static @NonNull UCode getCode(@Nullable UStatus status) {
        return getCode(status, UCode.UNKNOWN);
    }

    /**
     * Convert an <code>int</code> value to a code.
     *
     * @param value An <code>int</code> value to be mapped.
     * @return A {@link UCode} matching the given <code>value</code>, otherwise {@link UCode#UNKNOWN}.
     */
    static @NonNull UCode toCode(int value) {
        final UCode code = UCode.forNumber(value);
        return (code != null) ? code : UCode.UNKNOWN;
    }

    private static @NonNull UStatus.Builder newStatusBuilder(@NonNull UCode code) {
        return UStatus.newBuilder().setCode(code);
    }

    private static @NonNull UStatus.Builder newStatusBuilder(@NonNull UCode code, @Nullable String message) {
        UStatus.Builder builder = newStatusBuilder(code);
        if (message != null) {
            builder.setMessage(message);
        }
        return builder;
    }

    /**
     * Build a status.
     *
     * @param code A {@link UCode} to set.
     * @return A {@link UStatus} with the given <code>code</code>.
     */
    static @NonNull UStatus buildStatus(@NonNull UCode code) {
        return newStatusBuilder(code).build();
    }

    /**
     * Build a status.
     *
     * @param code    A {@link UCode} to set.
     * @param message A message to set.
     * @return A {@link UStatus} with the given <code>code</code> and <code>message</code>.
     */
    static @NonNull UStatus buildStatus(@NonNull UCode code, @Nullable String message) {
        return newStatusBuilder(code, message).build();
    }

    /**
     * Convert an exception to a status.
     *
     * @param exception A {@link Throwable} to convert.
     * @return A {@link UStatus} that includes a mapped code and a message derived from that <code>exception</code>.
     */
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

    /**
     * Ensure that a status is successful.
     *
     * @param status A {@link UStatus} to check.
     * @throws UStatusException containing <code>status</code> if it contains a code different from {@link UCode#OK}.
     */
    static void checkStatusOk(@NonNull UStatus status) {
        if (!isOk(status)) {
            throw new UStatusException(status);
        }
    }

    /**
     * Ensure the truth of an expression involving one or more parameters.
     *
     * @param expression   A boolean expression to check.
     * @param errorMessage A message to use if the check fails.
     * @throws UStatusException containing {@link UCode#INVALID_ARGUMENT} if <code>expression</code> is false.
     */
    static void checkArgument(boolean expression, @Nullable String errorMessage) {
        if (!expression) {
            throw new UStatusException(UCode.INVALID_ARGUMENT, errorMessage);
        }
    }

    /**
     * Ensure the truth of an expression involving one or more parameters.
     *
     * @param expression   A boolean expression to check.
     * @param errorCode    A {@link UCode} to use if the check fails.
     * @param errorMessage A message to use if the check fails.
     * @throws UStatusException containing <code>errorCode</code> if <code>expression</code> is false.
     */
    static void checkArgument(boolean expression, @NonNull UCode errorCode, @Nullable String errorMessage) {
        if (!expression) {
            throw new UStatusException(errorCode, errorMessage);
        }
    }

    /**
     * Ensure that an argument numeric value is positive.
     *
     * @param value        A numeric <code>int</code> value to check.
     * @param errorMessage A message to use if the check fails.
     * @return A validated <code>value</code>.
     * @throws UStatusException containing {@link UCode#INVALID_ARGUMENT} if <code>value</code> is not positive.
     */
    static int checkArgumentPositive(int value, @Nullable String errorMessage) {
        if (value <= 0) {
            throw new UStatusException(UCode.INVALID_ARGUMENT, errorMessage);
        }
        return value;
    }

    /**
     * Ensure that an argument numeric value is positive.
     *
     * @param value        A numeric <code>int</code> value to check.
     * @param errorCode    A {@link UCode} to use if the check fails.
     * @param errorMessage A message to use if the check fails.
     * @return A validated <code>value</code>.
     * @throws UStatusException containing <code>errorCode</code> if <code>value</code> is not positive.
     */
    static int checkArgumentPositive(int value, @NonNull UCode errorCode, @Nullable String errorMessage) {
        if (value <= 0) {
            throw new UStatusException(errorCode, errorMessage);
        }
        return value;
    }

    /**
     * Ensure that an argument numeric value is non-negative.
     *
     * @param value        A numeric <code>int</code> value to check.
     * @param errorMessage A message to use if the check fails.
     * @return A validated <code>value</code>.
     * @throws UStatusException containing {@link UCode#INVALID_ARGUMENT} if <code>value</code> is negative.
     */
    static int checkArgumentNonNegative(int value, @Nullable String errorMessage) {
        if (value < 0) {
            throw new UStatusException(UCode.INVALID_ARGUMENT, errorMessage);
        }
        return value;
    }

    /**
     * Ensure that an argument numeric value is non-negative.
     *
     * @param value        A numeric <code>int</code> value to check.
     * @param errorCode    A {@link UCode} to use if the check fails.
     * @param errorMessage A message to use if the check fails.
     * @return A validated <code>value</code>.
     * @throws UStatusException containing <code>errorCode</code> if <code>value</code> is negative.
     */
    static int checkArgumentNonNegative(int value, @NonNull UCode errorCode, @Nullable String errorMessage) {
        if (value < 0) {
            throw new UStatusException(errorCode, errorMessage);
        }
        return value;
    }

    /**
     * Ensure that a string is not empty.
     *
     * @param string       A string to check.
     * @param errorMessage A message to use if the check fails.
     * @return A validated <code>string</code>.
     * @throws UStatusException containing {@link UCode#INVALID_ARGUMENT} if <code>string</code> is empty or null.
     */
    static @NonNull <T extends CharSequence> T checkStringNotEmpty(T string, @Nullable String errorMessage) {
        if (TextUtils.isEmpty(string)) {
            throw new UStatusException(UCode.INVALID_ARGUMENT, errorMessage);
        }
        return string;
    }

    /**
     * Ensure that a string is not empty.
     *
     * @param string       A string to check.
     * @param errorCode    A {@link UCode} to use if the check fails.
     * @param errorMessage A message to use if the check fails.
     * @return A validated <code>string</code>.
     * @throws UStatusException containing <code>errorCode</code> if <code>string</code> is empty or null.
     */
    static @NonNull <T extends CharSequence> T checkStringNotEmpty(T string, @NonNull UCode errorCode,
            @Nullable String errorMessage) {
        if (TextUtils.isEmpty(string)) {
            throw new UStatusException(errorCode, errorMessage);
        }
        return string;
    }

    /**
     * Ensure that strings are equal.
     *
     * @param string1      A string.
     * @param string2      A string to be compared with <code>string1</code> for equality.
     * @param errorMessage A message to use if the check fails.
     * @return A validated <code>string1</code>.
     * @throws UStatusException containing {@link UCode#INVALID_ARGUMENT} if strings are not equal.
     */
    static @NonNull <T extends CharSequence> T checkStringEquals(T string1, T string2,
            @Nullable String errorMessage) {
        if (!TextUtils.equals(string1, string2)) {
            throw new UStatusException(UCode.INVALID_ARGUMENT, errorMessage);
        }
        return string1;
    }

    /**
     * Ensure that strings are equal.
     *
     * @param string1      A string.
     * @param string2      A string to be compared with <code>string1</code> for equality.
     * @param errorCode    A {@link UCode} to use if the check fails.
     * @param errorMessage A message to use if the check fails.
     * @return A validated <code>string1</code>.
     * @throws UStatusException containing <code>errorCode</code> if strings are not equal.
     */
    static @NonNull <T extends CharSequence> T checkStringEquals(T string1, @NonNull T string2,
            @NonNull UCode errorCode, @Nullable String errorMessage) {
        if (!TextUtils.equals(string1, string2)) {
            throw new UStatusException(errorCode, errorMessage);
        }
        return string1;
    }

    /**
     * Ensure that an object reference is not null.
     *
     * @param reference    An object reference to check.
     * @param errorMessage A message to use if the check fails.
     * @return A validated <code>reference</code>.
     * @throws UStatusException containing {@link UCode#INVALID_ARGUMENT} if <code>reference</code> is null.
     */
    static @NonNull <T> T checkNotNull(@Nullable T reference, @Nullable String errorMessage) {
        if (reference == null) {
            throw new UStatusException(UCode.INVALID_ARGUMENT, errorMessage);
        }
        return reference;
    }

    /**
     * Ensure that an object reference is not null.
     *
     * @param reference    An object reference to check.
     * @param errorCode    A {@link UCode} to use if the check fails.
     * @param errorMessage A message to use if the check fails.
     * @return A validated <code>reference</code>.
     * @throws UStatusException containing <code>errorCode</code> if <code>reference</code> is null.
     */
    static @NonNull <T> T checkNotNull(@Nullable T reference, @NonNull UCode errorCode,
            @Nullable String errorMessage) {
        if (reference == null) {
            throw new UStatusException(errorCode, errorMessage);
        }
        return reference;
    }

    /**
     * Ensure the truth of an expression involving a state.
     *
     * @param expression   A boolean expression to check.
     * @param errorMessage A message to use if the check fails.
     * @throws UStatusException containing {@link UCode#FAILED_PRECONDITION} if <code>expression</code> is false.
     */
    static void checkState(boolean expression, @Nullable String errorMessage) {
        if (!expression) {
            throw new UStatusException(UCode.FAILED_PRECONDITION, errorMessage);
        }
    }

    /**
     * Ensure the truth of an expression involving a state.
     *
     * @param expression   A boolean expression to check.
     * @param errorCode    A {@link UCode} to use if the check fails.
     * @param errorMessage A message to use if the check fails.
     * @throws UStatusException containing <code>errorCode</code> if <code>expression</code> is false.
     */
    static void checkState(boolean expression, @NonNull UCode errorCode, @Nullable String errorMessage) {
        if (!expression) {
            throw new UStatusException(errorCode, errorMessage);
        }
    }
}
