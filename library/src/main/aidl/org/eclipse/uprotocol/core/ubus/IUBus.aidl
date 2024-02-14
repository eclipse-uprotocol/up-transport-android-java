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

import org.eclipse.uprotocol.core.ubus.IUListener;
import org.eclipse.uprotocol.v1.internal.ParcelableUEntity;
import org.eclipse.uprotocol.v1.internal.ParcelableUMessage;
import org.eclipse.uprotocol.v1.internal.ParcelableUStatus;
import org.eclipse.uprotocol.v1.internal.ParcelableUUri;

interface IUBus {
    ParcelableUStatus registerClient(in String packageName, in ParcelableUEntity entity, in IBinder clientToken, in int flags, in IUListener listener);
    ParcelableUStatus unregisterClient(in IBinder clientToken);
    ParcelableUStatus send(in ParcelableUMessage message, in IBinder clientToken);
    @nullable ParcelableUMessage[] pull(in ParcelableUUri uri, int count, in int flags, IBinder clientToken);
    ParcelableUStatus enableDispatching(in ParcelableUUri uri, in int flags, IBinder clientToken);
    ParcelableUStatus disableDispatching(in ParcelableUUri uri, in int flags, IBinder clientToken);
}
