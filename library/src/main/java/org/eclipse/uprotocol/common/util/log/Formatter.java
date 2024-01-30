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

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.uri.serializer.LongUriSerializer;
import org.eclipse.uprotocol.uuid.serializer.LongUuidSerializer;
import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UResource;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUID;
import org.eclipse.uprotocol.v1.UUri;

/**
 * The formatter utility to be used for logging key-value pairs.
 */
public interface Formatter {
    /** The separator between a key and a value. */
    String SEPARATOR_PAIR = ": ";

    /** The separator between key-value pairs. */
    String SEPARATOR_PAIRS = ", ";

    /**
     * Format a tag with a given name.
     *
     * @param name A name of a tag.
     * @return A formatted tag.
     */
    static @NonNull String tag(@NonNull String name) {
        return tag(name, null);
    }

    /**
     * Format a tag with a given name and group.
     *
     * @param name A name of a tag.
     * @param group A group of a tag.
     * @return A formatted tag.
     */
    static @NonNull String tag(@NonNull String name, String group) {
        return isNullOrEmpty(group) ? name : name + ":" + group;
    }

    private static @NonNull String toString(Object object) {
        return (object == null) ? "" : object.toString();
    }

    private static @NonNull String escapeQuotes(String value) {
        return isNullOrEmpty(value) ? "" : value.replace("\"", "\\\"");
    }

    private static @NonNull String quoteIfNeeded(String value) {
        if (isNullOrEmpty(value) || value.charAt(0) == '"' ||  value.charAt(0) == '[') {
            return nullToEmpty(value);
        }
        return value.indexOf(' ') >= 0 ? quote(value) : value;
    }

    /**
     * Apply quotes and any necessary character escaping.
     *
     * @param value A string to modify.
     * @return A quoted string.
     */
    static @NonNull String quote(String value) {
        return '"' + escapeQuotes(value) + '"';
    }

    /**
     * Remove all quotes.
     *
     * @param value A string to modify.
     * @return A string without quotes.
     */
    static @NonNull String removeQuote(String value) {
        return value.replace("\"", "");
    }

    /**
     * Apply square brackets as a group.
     *
     * @param value A string to modify.
     * @return A string enclosed in square brackets.
     */
    static @NonNull String group(String value) {
        return '[' + nullToEmpty(value) + ']';
    }

    /**
     * Format grouped key-value pairs.
     *
     * @param args A variable argument list of key-value pairs, like "key1, value1, key2, value2, ...".
     * @return A formatted string containing grouped key-value pairs.
     */
    static @NonNull String joinGrouped(Object... args) {
        final StringBuilder builder = new StringBuilder("[");
        return joinAndAppend(builder, args).append("]").toString();
    }

    /**
     * Format key-value pairs.
     *
     * @param args A variable argument list of key-value pairs, like "key1, value1, key2, value2, ...".
     * @return A formatted string containing key-value pairs.
     */
    static @NonNull String join(Object... args) {
        return joinAndAppend(new StringBuilder(), args).toString();
    }

    /**
     * Format key-value pairs and append the result to a given {@link StringBuilder}.
     *
     * @param builder A {@link StringBuilder} to append the result.
     * @param args    A variable argument list of key-value pairs, like "key1, value1, key2, value2, ...".
     * @return A {@link StringBuilder} containing formatted key-value pairs.
     */
    static @NonNull StringBuilder joinAndAppend(@NonNull StringBuilder builder, Object... args) {
        if (args == null) {
            return builder;
        }
        boolean isKey = true;
        boolean skipValue = false;
        for (Object arg : args) {
            final String string = toString(arg);
            if (isKey && isNullOrEmpty(string) || skipValue) {
                isKey = !isKey;
                skipValue = !skipValue;
                continue;
            }
            if (isKey) {
                appendPairsSeparator(builder);
                builder.append(string);
            } else {
                builder.append(SEPARATOR_PAIR);
                builder.append(quoteIfNeeded(string));
            }
            isKey = !isKey;
        }
        return builder;
    }

    private static void appendPairsSeparator(@NonNull StringBuilder builder) {
        if (builder.length() > 1) {
            builder.append(SEPARATOR_PAIRS);
        }
    }

    /**
     * Format a status of a method with optional arguments.
     *
     * @param method A name of a method.
     * @param status A {@link UStatus} to format.
     * @param args   A variable argument list of key-value pairs, like "key1, value1, key2, value2, ...".
     * @return A formatted string containing a <code>method/status</code> pair and other given key-value pairs.
     */
    static @NonNull String status(@NonNull String method, @NonNull UStatus status, Object... args) {
        final StringBuilder builder = new StringBuilder();
        joinAndAppend(builder, Key.forStatus(method), Formatter.stringify(status));
        joinAndAppend(builder, args);
        return builder.toString();
    }

    /**
     * Convert a {@link UUID} into a string.
     *
     * @param id A {@link UUID} to convert.
     * @return A formatted string.
     */
    static @NonNull String stringify(UUID id) {
        return LongUuidSerializer.instance().serialize(id);
    }

    /**
     * Convert a {@link UEntity} into a string containing arbitrary fields.
     *
     * @param entity A {@link UEntity} to convert.
     * @return A formatted string.
     */
    static @NonNull String stringify(UEntity entity) {
        if (entity == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(entity.getName());
        if (entity.hasVersionMajor()) {
            sb.append('/').append(entity.getVersionMajor());
        }
        return sb.toString();
    }

    /**
     * Convert a {@link UResource} into a string containing arbitrary fields.
     *
     * @param resource A {@link UResource} to convert.
     * @return A formatted string.
     */
    static @NonNull String stringify(UResource resource) {
        if (resource == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(resource.getName());
        if (resource.hasInstance()) {
            sb.append('.').append(resource.getInstance());
        }
        if (resource.hasMessage()) {
            sb.append('#').append(resource.getMessage());
        }
        return sb.toString();
    }

    /**
     * Convert a {@link UUri} into a string.
     *
     * @param uri A {@link UUri} to convert.
     * @return A formatted string.
     */
    static @NonNull String stringify(UUri uri) {
        return LongUriSerializer.instance().serialize(uri);
    }

    /**
     * Convert a {@link UStatus} into a string.
     *
     * @param status A {@link UStatus} to convert.
     * @return A formatted string.
     */
    static @NonNull String stringify(UStatus status) {
        if (status == null) {
            return "";
        }
        final boolean hasMessage = status.hasMessage();
        return joinGrouped(Key.CODE, status.getCode(),
                hasMessage ? Key.MESSAGE : null, hasMessage ? quote(status.getMessage()) : null);
    }

    /**
     * Convert a {@link UMessage} into a string containing arbitrary fields.
     *
     * @param message A {@link UMessage} to convert.
     * @return A formatted string.
     */
    static @NonNull String stringify(UMessage message) {
        if (message == null) {
            return "";
        }
        final UAttributes attributes = message.getAttributes();
        final boolean hasSink = attributes.hasSink();
        return joinGrouped(Key.ID, stringify(attributes.getId()), Key.SOURCE, stringify(message.getSource()),
                hasSink ? Key.SINK : null, hasSink ? stringify(attributes.getSink()) : null,
                Key.TYPE, attributes.getType());
    }

    /**
     * Convert a byte count to a human readable string.
     *
     * @param bytes A byte count.
     * @return A formatted string such as "5.0 MB".
     */
    @SuppressLint("DefaultLocale")
    static @NonNull String toPrettyMemory(long bytes) {
        long unit = 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), "KMGTPE".charAt(exp - 1));
    }
}
