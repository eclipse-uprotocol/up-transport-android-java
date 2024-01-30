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
import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.TestBase;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UStatus;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UStatusExceptionTest extends TestBase {
    private static final UCode CODE = UCode.OK;
    private static final String MESSAGE = "Test message";
    private static final UStatus STATUS = UStatus.newBuilder().setCode(CODE).setMessage(MESSAGE).build();
    private static final Throwable CAUSE = new Throwable(MESSAGE);

    @Test
    public void testConstructorWithStatus() {
        final UStatusException exception = new UStatusException(STATUS);
        assertEquals(STATUS, exception.getStatus());
        assertEquals(CODE, exception.getCode());
        assertEquals(MESSAGE, exception.getMessage());
    }

    @Test
    public void testConstructorWithStatusAndCause() {
        final UStatusException exception = new UStatusException(STATUS, CAUSE);
        assertEquals(STATUS, exception.getStatus());
        assertEquals(CODE, exception.getCode());
        assertEquals(MESSAGE, exception.getMessage());
        assertEquals(CAUSE, exception.getCause());
    }

    @Test
    public void testConstructorWithCodeAndMessage() {
        final UStatusException exception = new UStatusException(CODE, MESSAGE);
        assertEquals(STATUS, exception.getStatus());
        assertEquals(CODE, exception.getCode());
        assertEquals(MESSAGE, exception.getMessage());
    }

    @Test
    public void testConstructorWithCodeMessageAndCause() {
        final UStatusException exception = new UStatusException(CODE, MESSAGE, CAUSE);
        assertEquals(STATUS, exception.getStatus());
        assertEquals(CODE, exception.getCode());
        assertEquals(MESSAGE, exception.getMessage());
        assertEquals(CAUSE, exception.getCause());
    }

    @Test
    public void testConstructorNegative() {
        final UStatusException exception = new UStatusException(null);
        assertEquals(buildStatus(UCode.UNKNOWN), exception.getStatus());
        assertEquals(UCode.UNKNOWN, exception.getCode());
        assertEquals("", exception.getMessage());
    }

    @Test
    public void testGetStatus() {
        final UStatusException exception = new UStatusException(STATUS);
        assertEquals(STATUS, exception.getStatus());
    }

    @Test
    public void testGetCode() {
        final UStatusException exception = new UStatusException(STATUS);
        assertEquals(CODE, exception.getCode());
    }

    @Test
    public void testGetMessage() {
        final UStatusException exception = new UStatusException(STATUS);
        assertEquals(MESSAGE, exception.getMessage());
    }
}
