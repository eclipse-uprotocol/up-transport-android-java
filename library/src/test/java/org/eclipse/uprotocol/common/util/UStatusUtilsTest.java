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

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.protobuf.InvalidProtocolBufferException;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UStatus;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
@SuppressWarnings({"ConstantValue", "DataFlowIssue"})
public class UStatusUtilsTest {
    private static final UStatus STATUS_UNKNOWN = UStatusUtils.buildStatus(UCode.UNKNOWN, "Unknown");
    private static final String MESSAGE = "Test message";

    @Test
    public void testIsOk() {
        assertTrue(UStatusUtils.isOk(STATUS_OK));
        assertFalse(UStatusUtils.isOk(STATUS_UNKNOWN));
    }

    @Test
    public void testIsOkNegative() {
        assertFalse(UStatusUtils.isOk(null));
    }

    @Test
    public void testHasCodeInt() {
        assertTrue(UStatusUtils.hasCode(STATUS_OK, UCode.OK_VALUE));
        assertFalse(UStatusUtils.hasCode(STATUS_OK, UCode.UNKNOWN_VALUE));
    }

    @Test
    public void testHasCodeIntNegative() {
        assertFalse(UStatusUtils.hasCode(null, UCode.OK_VALUE));
    }

    @Test
    public void testHasCode() {
        assertTrue(UStatusUtils.hasCode(STATUS_OK, UCode.OK));
        assertFalse(UStatusUtils.hasCode(STATUS_OK, UCode.UNKNOWN));
    }

    @Test
    public void testHasCodeNegative() {
        assertFalse(UStatusUtils.hasCode(null, UCode.OK));
    }

    @Test
    public void testGetCode() {
        assertEquals(UCode.OK, UStatusUtils.getCode(STATUS_OK));
        assertEquals(UCode.OK, UStatusUtils.getCode(STATUS_OK, UCode.UNKNOWN));
    }

    @Test
    public void testGetCodeNegative() {
        assertEquals(UCode.UNKNOWN, UStatusUtils.getCode(null));
        assertEquals(UCode.INTERNAL, UStatusUtils.getCode(null, UCode.INTERNAL));
    }

    @Test
    public void testToCode() {
        assertEquals(UCode.OK, UStatusUtils.toCode(UCode.OK_VALUE));
        assertEquals(UCode.DATA_LOSS, UStatusUtils.toCode(UCode.DATA_LOSS_VALUE));
    }

    @Test
    public void testToCodeNegative() {
        assertEquals(UCode.UNKNOWN, UStatusUtils.toCode(-1));
        assertEquals(UCode.UNKNOWN, UStatusUtils.toCode(100));
    }

    @Test
    public void testBuildStatusCode() {
        final UStatus status = UStatusUtils.buildStatus(UCode.INVALID_ARGUMENT);
        assertEquals(UCode.INVALID_ARGUMENT, status.getCode());
    }

    @Test
    public void testBuildStatusCodeAndMessage() {
        final UStatus status = UStatusUtils.buildStatus(UCode.INVALID_ARGUMENT, MESSAGE);
        assertEquals(UCode.INVALID_ARGUMENT, status.getCode());
        assertEquals(MESSAGE, status.getMessage());
    }

    @Test
    public void testToStatusUStatusException() {
        final UStatus status = UStatusUtils.toStatus(new UStatusException(UCode.UNIMPLEMENTED, MESSAGE));
        assertEquals(UCode.UNIMPLEMENTED, status.getCode());
        assertEquals(MESSAGE, status.getMessage());
    }

    @Test
    public void testToStatusCompletionException() {
        final Throwable cause = new IllegalArgumentException(MESSAGE);
        final UStatus status = UStatusUtils.toStatus(new CompletionException(cause));
        assertEquals(UCode.INVALID_ARGUMENT, status.getCode());
        assertEquals(MESSAGE, status.getMessage());
    }

    @Test
    public void testToStatusCompletionExceptionWithStatus() {
        final Throwable cause = new UStatusException(UCode.NOT_FOUND, MESSAGE);
        final UStatus status = UStatusUtils.toStatus(new CompletionException(cause));
        assertEquals(UCode.NOT_FOUND, status.getCode());
        assertEquals(MESSAGE, status.getMessage());
    }

    @Test
    public void testToStatusCompletionExceptionWithoutCause() {
        final UStatus status = UStatusUtils.toStatus(new CompletionException(null));
        assertEquals(UCode.UNKNOWN, status.getCode());
        assertEquals("", status.getMessage());
    }

    @Test
    public void testToStatusExecutionException() {
        final Throwable cause = new IllegalArgumentException(MESSAGE);
        final UStatus status = UStatusUtils.toStatus(new ExecutionException(cause));
        assertEquals(UCode.INVALID_ARGUMENT, status.getCode());
        assertEquals(MESSAGE, status.getMessage());
    }

    @Test
    public void testToStatusExecutionExceptionWithStatus() {
        final Throwable cause = new UStatusException(UCode.NOT_FOUND, MESSAGE);
        final UStatus status = UStatusUtils.toStatus(new ExecutionException(cause));
        assertEquals(UCode.NOT_FOUND, status.getCode());
        assertEquals(MESSAGE, status.getMessage());
    }

    @Test
    public void testToStatusExecutionExceptionWithoutCause() {
        final UStatus status = UStatusUtils.toStatus(new ExecutionException(null));
        assertEquals(UCode.UNKNOWN, status.getCode());
        assertEquals("", status.getMessage());
    }

    @Test
    public void testToStatusSecurityException() {
        final UStatus status = UStatusUtils.toStatus(new SecurityException(MESSAGE));
        assertEquals(UCode.PERMISSION_DENIED, status.getCode());
        assertEquals(MESSAGE, status.getMessage());
    }

    @Test
    public void testToStatusInvalidProtocolBufferException() {
        final UStatus status = UStatusUtils.toStatus(new InvalidProtocolBufferException(MESSAGE));
        assertEquals(UCode.INVALID_ARGUMENT, status.getCode());
        assertEquals(MESSAGE, status.getMessage());
    }

    @Test
    public void testToStatusIllegalArgumentException() {
        final UStatus status = UStatusUtils.toStatus(new IllegalArgumentException(MESSAGE));
        assertEquals(UCode.INVALID_ARGUMENT, status.getCode());
        assertEquals(MESSAGE, status.getMessage());
    }

    @Test
    public void testToStatusNullPointerException() {
        final UStatus status = UStatusUtils.toStatus(new NullPointerException(MESSAGE));
        assertEquals(UCode.INVALID_ARGUMENT, status.getCode());
        assertEquals(MESSAGE, status.getMessage());
    }

    @Test
    public void testToStatusCancellationException() {
        final UStatus status = UStatusUtils.toStatus(new CancellationException(MESSAGE));
        assertEquals(UCode.CANCELLED, status.getCode());
        assertEquals(MESSAGE, status.getMessage());
    }

    @Test
    public void testToStatusIllegalStateException() {
        final UStatus status = UStatusUtils.toStatus(new IllegalStateException(MESSAGE));
        assertEquals(UCode.UNAVAILABLE, status.getCode());
        assertEquals(MESSAGE, status.getMessage());
    }

    @Test
    public void testToStatusRemoteException() {
        final UStatus status = UStatusUtils.toStatus(new RemoteException(MESSAGE));
        assertEquals(UCode.UNAVAILABLE, status.getCode());
        assertEquals(MESSAGE, status.getMessage());
    }

    @Test
    public void testToStatusUnsupportedOperationException() {
        final UStatus status = UStatusUtils.toStatus(new UnsupportedOperationException(MESSAGE));
        assertEquals(UCode.UNIMPLEMENTED, status.getCode());
        assertEquals(MESSAGE, status.getMessage());
    }

    @Test
    public void testToStatusCancelledException() {
        final UStatus status = UStatusUtils.toStatus(new InterruptedException(MESSAGE));
        assertEquals(UCode.CANCELLED, status.getCode());
        assertEquals(MESSAGE, status.getMessage());
    }

    @Test
    public void testToStatusDeadlineException() {
        final UStatus status = UStatusUtils.toStatus(new TimeoutException(MESSAGE));
        assertEquals(UCode.DEADLINE_EXCEEDED, status.getCode());
        assertEquals(MESSAGE, status.getMessage());
    }

    @Test
    public void testToStatusUnknownException() {
        final UStatus status = UStatusUtils.toStatus(new RuntimeException(MESSAGE));
        assertEquals(UCode.UNKNOWN, status.getCode());
        assertEquals(MESSAGE, status.getMessage());
    }

    @Test
    public void testCheckStatusOk() {
        UStatusUtils.checkStatusOk(STATUS_OK);
        final UStatusException exception = assertThrows(UStatusException.class,
                () -> UStatusUtils.checkStatusOk(STATUS_UNKNOWN));
        assertEquals(STATUS_UNKNOWN, exception.getStatus());
    }

    @Test
    public void testCheckArgument() {
        UStatusUtils.checkArgument(true, MESSAGE);
    }

    @Test
    public void testCheckArgumentNegative() {
        final UStatusException exception = assertThrows(UStatusException.class,
                () -> UStatusUtils.checkArgument(false, MESSAGE));
        assertEquals(UCode.INVALID_ARGUMENT, exception.getCode());
        assertEquals(MESSAGE, exception.getMessage());
    }

    @Test
    public void testCheckArgumentWithCode() {
        UStatusUtils.checkArgument(true, UCode.OK, MESSAGE);
    }

    @Test
    public void testCheckArgumentWithCodeNegative() {
        final UCode code = UCode.INVALID_ARGUMENT;
        final UStatusException exception = assertThrows(UStatusException.class,
                () -> UStatusUtils.checkArgument(false, code, MESSAGE));
        assertEquals(code, exception.getCode());
        assertEquals(MESSAGE, exception.getMessage());
    }

    @Test
    public void testCheckArgumentPositive() {
        final int value = 1;
        assertEquals(value, UStatusUtils.checkArgumentPositive(value, MESSAGE));
    }

    @Test
    public void testCheckArgumentPositiveNegative() {
        final int value = 0;
        final UStatusException exception = assertThrows(UStatusException.class,
                () -> UStatusUtils.checkArgumentPositive(value, MESSAGE));
        assertEquals(UCode.INVALID_ARGUMENT, exception.getCode());
        assertEquals(MESSAGE, exception.getMessage());
    }

    @Test
    public void testCheckArgumentPositiveWithCode() {
        final int value = 1;
        assertEquals(value, UStatusUtils.checkArgumentPositive(value, UCode.UNKNOWN, MESSAGE));
    }

    @Test
    public void testCheckArgumentPositiveExceptionWithCode() {
        final int value = 0;
        final UCode code = UCode.CANCELLED;
        final UStatusException exception = assertThrows(UStatusException.class,
                () -> UStatusUtils.checkArgumentPositive(value, code, MESSAGE));
        assertEquals(code, exception.getCode());
        assertEquals(MESSAGE, exception.getMessage());
    }

    @Test
    public void testCheckArgumentNonNegative() {
        final int value = 1;
        assertEquals(value, UStatusUtils.checkArgumentNonNegative(value, MESSAGE));
    }

    @Test
    public void testCheckArgumentNonNegativeNegative() {
        final int value = -1;
        final UStatusException exception = assertThrows(UStatusException.class,
                () -> UStatusUtils.checkArgumentNonNegative(value, MESSAGE));
        assertEquals(UCode.INVALID_ARGUMENT, exception.getCode());
        assertEquals(MESSAGE, exception.getMessage());
    }

    @Test
    public void testCheckArgumentNonNegativeWithCode() {
        final int value = 1;
        assertEquals(value, UStatusUtils.checkArgumentNonNegative(value, UCode.UNKNOWN, MESSAGE));
    }

    @Test
    public void testCheckArgumentNonNegativeWithCodeNegative() {
        final int value = -1;
        final UCode code = UCode.CANCELLED;
        final UStatusException exception = assertThrows(UStatusException.class,
                () -> UStatusUtils.checkArgumentNonNegative(value, code, MESSAGE));
        assertEquals(code, exception.getCode());
        assertEquals(MESSAGE, exception.getMessage());
    }

    @Test
    public void testCheckStringNotEmpty() {
        final String string = "test";
        assertEquals(string, UStatusUtils.checkStringNotEmpty(string, MESSAGE));
    }

    @Test
    public void testCheckStringNotEmptyNegative() {
        final UStatusException exception1 = assertThrows(UStatusException.class,
                () -> UStatusUtils.checkStringNotEmpty("", MESSAGE));
        assertEquals(UCode.INVALID_ARGUMENT, exception1.getCode());
        assertEquals(MESSAGE, exception1.getMessage());

        final UStatusException exception2 = assertThrows(UStatusException.class,
                () -> UStatusUtils.checkStringNotEmpty(null, MESSAGE));
        assertEquals(UCode.INVALID_ARGUMENT, exception2.getCode());
        assertEquals(MESSAGE, exception2.getMessage());
    }

    @Test
    public void testCheckStringNotEmptyWithCode() {
        final String string = "test";
        assertEquals(string, UStatusUtils.checkStringNotEmpty(string, UCode.UNKNOWN, MESSAGE));
    }

    @Test
    public void testCheckStringNotEmptyWithCodeNegative() {
        final UCode code = UCode.INTERNAL;
        final UStatusException exception1 = assertThrows(UStatusException.class,
                () -> UStatusUtils.checkStringNotEmpty("", code, MESSAGE));
        assertEquals(code, exception1.getCode());
        assertEquals(MESSAGE, exception1.getMessage());

        final UStatusException exception2 = assertThrows(UStatusException.class,
                () -> UStatusUtils.checkStringNotEmpty(null, code, MESSAGE));
        assertEquals(code, exception2.getCode());
        assertEquals(MESSAGE, exception2.getMessage());
    }

    @Test
    public void testCheckStringEquals() {
        final String string1 = "test";
        final String string2 = "test";
        assertEquals(string1, UStatusUtils.checkStringEquals(string1, string2, MESSAGE));
    }

    @Test
    public void testCheckStringEqualsNegative() {
        final String string1 = "test1";
        final String string2 = "test2";
        final UStatusException exception = assertThrows(UStatusException.class,
                () -> UStatusUtils.checkStringEquals(string1, string2, MESSAGE));
        assertEquals(UCode.INVALID_ARGUMENT, exception.getCode());
        assertEquals(MESSAGE, exception.getMessage());
    }

    @Test
    public void testCheckStringEqualsWithCode() {
        final String string1 = "test";
        final String string2 = "test";
        assertEquals(string1, UStatusUtils.checkStringEquals(string1, string2, UCode.UNKNOWN, MESSAGE));
    }

    @Test
    public void testCheckStringEqualsWithCodeNegative() {
        final String string1 = "test1";
        final String string2 = "test2";
        final UCode code = UCode.UNKNOWN;
        final UStatusException exception = assertThrows(UStatusException.class,
                () -> UStatusUtils.checkStringEquals(string1, string2, code, MESSAGE));
        assertEquals(code, exception.getCode());
        assertEquals(MESSAGE, exception.getMessage());
    }

    @Test
    public void testCheckNotNull() {
        final Object reference = new Object();
        assertEquals(reference, UStatusUtils.checkNotNull(reference, MESSAGE));
    }

    @Test
    public void testCheckNotNullNegative() {
        final UStatusException exception = assertThrows(UStatusException.class,
                () -> UStatusUtils.checkNotNull(null, MESSAGE));
        assertEquals(UCode.INVALID_ARGUMENT, exception.getCode());
        assertEquals(MESSAGE, exception.getMessage());
    }

    @Test
    public void testCheckNotNullWithCode() {
        final Object reference = new Object();
        assertEquals(reference, UStatusUtils.checkNotNull(reference, UCode.UNKNOWN, MESSAGE));
    }

    @Test
    public void testCheckNotNullWithCodeNegative() {
        final UCode code = UCode.UNKNOWN;
        final UStatusException testException = assertThrows(UStatusException.class,
                () -> UStatusUtils.checkNotNull(null, code, MESSAGE));
        assertEquals(code, testException.getCode());
        assertEquals(MESSAGE, testException.getMessage());
    }

    @Test
    public void testCheckState() {
        UStatusUtils.checkState(true, MESSAGE);
    }

    @Test
    public void testCheckStateNegative() {
        final UStatusException exception = assertThrows(UStatusException.class,
                () -> UStatusUtils.checkState(false, MESSAGE));
        assertEquals(UCode.FAILED_PRECONDITION, exception.getCode());
        assertEquals(MESSAGE, exception.getMessage());
    }

    @Test
    public void testCheckStateWithCode() {
        UStatusUtils.checkState(true, UCode.UNKNOWN, MESSAGE);
    }

    @Test
    public void testCheckStateWithCodeNegative() {
        final UCode code = UCode.UNKNOWN;
        final UStatusException exception = assertThrows(UStatusException.class,
                () -> UStatusUtils.checkState(false, code, MESSAGE));
        assertEquals(code, exception.getCode());
        assertEquals(MESSAGE, exception.getMessage());
    }
}
