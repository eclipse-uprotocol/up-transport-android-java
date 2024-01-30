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

import androidx.annotation.NonNull;

/**
 * The common keys to be used for logging key-value pairs.
 */
@SuppressWarnings("unused")
public interface Key {
    String ACCESS = "access";
    String ACTION = "action";
    String ATTRIBUTES = "attributes";
    String AUTHORITY = "authority";
    String CLASS = "class";
    String CLIENT = "client";
    String CODE = "code";
    String COMPONENT = "component";
    String CONNECTION = "connection";
    String COUNT = "count";
    String DATA = "data";
    String DEFAULT_LEVEL = "defaultLevel";
    String DELAY = "delay";
    String DUMP = "dump";
    String DURATION = "duration";
    String ENTITY = "entity";
    String EVENT = "event";
    String FAILURE = "failure";
    String FILENAME = "filename";
    String FORMAT = "format";
    String ID = "id";
    String INSTANCE = "instance";
    String INTENT = "intent";
    String IP = "ip";
    String LATENCY = "latency";
    String LEVEL = "level";
    String LEVELS = "levels";
    String MAJOR = "major";
    String MESSAGE = "message";
    String METHOD = "method";
    String MINOR = "minor";
    String MODE = "mode";
    String NAME = "name";
    String PACKAGE = "package";
    String PATH = "path";
    String PAYLOAD = "payload";
    String PERCENTAGE = "percentage";
    String PERMISSIONS = "permissions";
    String PID = "pid";
    String PRIORITY = "priority";
    String REASON = "reason";
    String REFERENCE = "reference";
    String REQUEST = "request";
    String REQUEST_ID = "requestId";
    String RESOURCE = "resource";
    String RESPONSE = "response";
    String SCOPE = "scope";
    String SERVER = "server";
    String SERVICE = "service";
    String SINK = "sink";
    String SIZE = "size";
    String SOURCE = "source";
    String STATE = "state";
    String STATUS = "status";
    String SUBSCRIBER = "subscriber";
    String SUBSCRIPTION = "subscription";
    String TIME = "time";
    String TIMEOUT = "timeout";
    String TOKEN = "token";
    String TOPIC = "topic";
    String TRIGGER = "trigger";
    String TTL = "ttl";
    String TYPE = "type";
    String UID = "uid";
    String URI = "uri";
    String USER = "user";
    String VALUE = "value";
    String VERSION = "version";

    /**
     * Format a status key for a given method.
     *
     * @param method A name of a method.
     * @return A formatted status key, like "status.method".
     */
    static @NonNull String forStatus(@NonNull String method) {
        return STATUS + "." + method;
    }
}
