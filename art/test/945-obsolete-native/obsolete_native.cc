/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <inttypes.h>
#include <memory>
#include <stdio.h>

#include "android-base/stringprintf.h"

#include "android-base/stringprintf.h"
#include "base/logging.h"
#include "base/macros.h"
#include "jni.h"
#include "jvmti.h"
#include "ScopedLocalRef.h"
#include "ti-agent/common_helper.h"
#include "ti-agent/common_load.h"

namespace art {
namespace Test945ObsoleteNative {

extern "C" JNIEXPORT void JNICALL Java_Main_bindTest945ObsoleteNative(
    JNIEnv* env, jclass klass ATTRIBUTE_UNUSED) {
  BindFunctions(jvmti_env, env, "Transform");
}

extern "C" JNIEXPORT void JNICALL Java_Transform_doExecute(JNIEnv* env,
                                                           jclass klass ATTRIBUTE_UNUSED,
                                                           jobject runnable) {
  jclass runnable_klass = env->FindClass("java/lang/Runnable");
  DCHECK(runnable_klass != nullptr);
  jmethodID run_method = env->GetMethodID(runnable_klass, "run", "()V");
  env->CallVoidMethod(runnable, run_method);
}


}  // namespace Test945ObsoleteNative
}  // namespace art
