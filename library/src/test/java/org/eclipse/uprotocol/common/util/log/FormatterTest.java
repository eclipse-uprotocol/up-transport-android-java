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
package org.eclipse.uprotocol.common.util.log;

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.transport.builder.UMessageBuilder.notification;
import static org.eclipse.uprotocol.transport.builder.UMessageBuilder.publish;
import static org.eclipse.uprotocol.uri.factory.UriFactory.ANY;
import static org.eclipse.uprotocol.uuid.serializer.UuidSerializer.serialize;
import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.TestBase;
import org.eclipse.uprotocol.uri.validator.UriFilter;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUID;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FormatterTest extends TestBase {
    private static final String NAME = "name";
    private static final String GROUP = "group";
    private static final String KEY1 = "key1";
    private static final String KEY2 = "key2";
    private static final String VALUE1 = "value1";
    private static final String VALUE2 = "value2";
    private static final String METHOD = "method";

    @Test
    public void testTag() {
        assertEquals(NAME + ":" + GROUP, Formatter.tag(NAME, GROUP));
    }

    @Test
    public void testConstructorWithEmptyGroup() {
        assertEquals(NAME, Formatter.tag(NAME));
        assertEquals(NAME, Formatter.tag(NAME, null));
        assertEquals(NAME, Formatter.tag(NAME, ""));
    }

    @Test
    public void testQuote() {
        assertEquals("\"test\"", Formatter.quote("test"));
    }

    @Test
    public void testQuoteNull() {
        assertEquals("\"\"", Formatter.quote(null));
    }

    @Test
    public void testQuoteEscaped() {
        assertEquals("\"with \\\" inside\"", Formatter.quote("with \" inside"));
    }

    @Test
    public void testRemoveQuotes() {
        assertEquals("with inside", Formatter.removeQuote("with \"inside"));
    }

    @Test
    public void testGroup() {
        assertEquals("[test]", Formatter.group("test"));
    }

    @Test
    public void testGroupEmpty() {
        assertEquals("[]", Formatter.group(""));
        assertEquals("[]", Formatter.group(null));
    }

    @Test
    public void testJoinGrouped() {
        assertEquals("[key1: value1]", Formatter.joinGrouped(KEY1, VALUE1));
    }

    @Test
    public void testJoin() {
        assertEquals("key1: value1, key2: value2", Formatter.join(KEY1, VALUE1, KEY2, VALUE2));
    }

    @Test
    public void testJoinEmpty() {
        assertEquals("", Formatter.join());
        assertEquals("", Formatter.join((Object[]) null));
    }

    @Test
    public void testJoinEmptyKey() {
        assertEquals("key1: value1", Formatter.join(KEY1, VALUE1, null, VALUE2));
        assertEquals("key1: value1", Formatter.join(KEY1, VALUE1, "", VALUE2));
    }

    @Test
    public void testJoinNullValue() {
        assertEquals("key1: ", Formatter.join(KEY1, null));
    }

    @Test
    public void testJoinAndAppend() {
        StringBuilder builder = new StringBuilder();
        Formatter.joinAndAppend(builder, KEY1, VALUE1);
        Formatter.joinAndAppend(builder, KEY2, VALUE2);
        assertEquals("key1: value1, key2: value2", builder.toString());
    }

    @Test
    public void testJoinAndAppendAutoQuotes() {
        StringBuilder builder = new StringBuilder();
        Formatter.joinAndAppend(builder, KEY1, "Value with spaces");
        Formatter.joinAndAppend(builder, KEY2, "[\"Quotes in group\"]");
        assertEquals("key1: \"Value with spaces\", key2: [\"Quotes in group\"]", builder.toString());
    }

    @Test
    public void testStatus() {
        assertEquals("status.method: [code: OK], key1: value1", Formatter.status(METHOD, STATUS_OK, KEY1, VALUE1));
    }

    @Test
    public void testError() {
        assertEquals("error: Message, reason: Reason, key1: value1",
                Formatter.error("Message", new RuntimeException("Reason"), KEY1, VALUE1));
    }

    @Test
    public void testStringifyUUID() {
        assertEquals(serialize(ID), Formatter.stringify(ID));
    }

    @Test
    public void testStringifyUUIDNull() {
        assertEquals("", Formatter.stringify((UUID) null));
    }

    @Test
    public void testStringifyUUri() {
        assertEquals("/51/1/8000", Formatter.stringify(RESOURCE_URI));
    }

    @Test
    public void testStringifyUUriNull() {
        assertEquals("", Formatter.stringify((UUri) null));
    }


    @Test
    public void testStringifyUriFilter() {
        assertEquals("[source: //*/ffff/ff/ffff, sink: /51/1/1]", Formatter.stringify(new UriFilter(ANY, METHOD_URI)));
    }

    @Test
    public void testStringifyUriFilterNull() {
        assertEquals("", Formatter.stringify((UriFilter) null));
    }

    @Test
    public void testStringifyUStatus() {
        final UStatus status = buildStatus(UCode.UNKNOWN, "Unknown failure");
        assertEquals("[code: UNKNOWN, message: \"Unknown failure\"]", Formatter.stringify(status));
    }

    @Test
    public void testStringifyUStatusWithoutMessage() {
        final UStatus status = buildStatus(UCode.OK);
        assertEquals("[code: OK]", Formatter.stringify(status));
    }

    @Test
    public void testStringifyUStatusNull() {
        assertEquals("", Formatter.stringify((UStatus) null));
    }

    @Test
    public void testStringifyUMessage() {
        final UMessage message = notification(RESOURCE_URI, CLIENT_URI).build();
        assertEquals("[id: " + serialize(message.getAttributes().getId()) + ", " +
                "source: /51/1/8000, sink: /50/1/0, type: UMESSAGE_TYPE_NOTIFICATION]", Formatter.stringify(message));
    }

    @Test
    public void testStringifyUMessageWithoutSink() {
        final UMessage message = publish(RESOURCE_URI).build();
        assertEquals("[id: " + serialize(message.getAttributes().getId()) + ", " +
                "source: /51/1/8000, type: UMESSAGE_TYPE_PUBLISH]", Formatter.stringify(message));
    }

    @Test
    public void testStringifyUMessageNull() {
        assertEquals("", Formatter.stringify((UMessage) null));
    }

    @Test
    public void testToPrettyMemory() {
        assertEquals("17 B", Formatter.toPrettyMemory(17));
        assertEquals("1.0 KB", Formatter.toPrettyMemory(1024));
        assertEquals("1.0 MB", Formatter.toPrettyMemory(1048576));
        assertEquals("1.0 GB", Formatter.toPrettyMemory(1073741824));
    }

    @Test
    public void testToPrettyDuration() {
        assertEquals("0h 0m 0s", Formatter.toPrettyDuration(0));
        assertEquals("2h 3m 4s", Formatter.toPrettyDuration(2 * 60 * 60 * 1000 + 3 * 60 * 1000 + 4 * 1000));
        assertEquals("0h 0m -1s", Formatter.toPrettyDuration( -1000));
    }
}
