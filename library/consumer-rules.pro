#
# Copyright (c) 2024 General Motors GTO LLC.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v. 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0.
#
# SPDX-FileType: DOCUMENTATION
# SPDX-FileCopyrightText: 2023 General Motors GTO LLC
# SPDX-License-Identifier: Apache-2.0
#
-keepclasseswithmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator CREATOR;
}

-keepclasseswithmembers class com.google.** {*;}

-keep class * extends com.google.protobuf.** {*;}
-keep class * extends com.google.protobuf.**$** {*;}

-keep public class com.ultifi.core.** {
    public protected *;
    <init>();
}

-keep public interface com.ultifi.core.** {
    *;
}
