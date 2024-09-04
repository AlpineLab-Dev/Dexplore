/*
 * Copyright (C) 2022 NeonOrbit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.neonorbit.dexplore;

import io.github.neonorbit.dexplore.reference.FieldRefData;
import io.github.neonorbit.dexplore.reference.MethodRefData;
import io.github.neonorbit.dexplore.reference.StringRefData;
import io.github.neonorbit.dexplore.reference.TypeRefData;
import io.github.neonorbit.dexplore.filter.ReferenceTypes;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

final class RefsPoolBuffer {
  private boolean needsCopy;
  private List<StringRefData> strings;
  private List<TypeRefData> types;
  private List<FieldRefData> fields;
  private List<MethodRefData> methods;
  private final boolean fieldDetails, methodDetails;

  RefsPoolBuffer(ReferenceTypes types) {
    this.strings = new ArrayList<>();
    this.types = new ArrayList<>();
    this.fields = new ArrayList<>();
    this.methods = new ArrayList<>();
    this.fieldDetails = types.hasFieldDetails();
    this.methodDetails = types.hasMethodDetails();
  }

  private void update() {
    if (needsCopy) {
      needsCopy = false;
      strings = new ArrayList<>(strings);
      types = new ArrayList<>(types);
      fields = new ArrayList<>(fields);
      methods = new ArrayList<>(methods);
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void resolve() {
    strings.forEach(StringRefData::toString);
    types.forEach(TypeRefData::toString);
    fields.forEach(FieldRefData::toString);
    methods.forEach(MethodRefData::toString);
  }

  @Nonnull
  public ReferencePool getPool() {
    needsCopy = true;
    return ReferencePool.build(strings, types, fields, methods);
  }

  @Nonnull
  public ReferencePool getPool(boolean resolve) {
    if (resolve) resolve();
    return getPool();
  }

  public void add(@Nonnull String value) {
    update();
    strings.add(StringRefData.build(value));
  }

  public void add(@Nonnull StringReference value) {
    update();
    strings.add(StringRefData.build(value));
  }

  public void add(@Nonnull TypeReference value) {
    update();
    types.add(TypeRefData.build(value));
  }

  public void add(@Nonnull FieldReference value) {
    update();
    fields.add(FieldRefData.build(value, fieldDetails));
  }

  public void add(@Nonnull MethodReference value) {
    update();
    methods.add(MethodRefData.build(value, methodDetails));
  }
}
