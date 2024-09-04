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

import io.github.neonorbit.dexplore.filter.ReferenceTypes;
import io.github.neonorbit.dexplore.iface.Internal;
import io.github.neonorbit.dexplore.util.DexLog;
import io.github.neonorbit.dexplore.util.DexUtils;
import com.android.tools.smali.dexlib2.ValueType;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedClassDef;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedField;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedMethod;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.DualReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import com.android.tools.smali.dexlib2.iface.value.DoubleEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.EncodedValue;
import com.android.tools.smali.dexlib2.iface.value.FloatEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.IntEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.LongEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.ShortEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Internal
public final class DexDecoder {
  private final boolean cache;
  private final RefsPoolCache<DexEntry> dexCache;
  private final RefsPoolCache<DexBackedClassDef> classCache;

  DexDecoder(DexOptions options) {
    this.cache = options.enableCache;
    this.dexCache = new RefsPoolCache<>();
    this.classCache = new RefsPoolCache<>();
  }

  @Nonnull
  public ReferencePool decode(@Nonnull DexEntry dexEntry,
                              @Nonnull ReferenceTypes types) {
    if (types.hasNone()) return ReferencePool.emptyPool();
    ReferencePool pool = cache ? dexCache.get(dexEntry, types) : null;
    if (pool == null) {
      pool = decodeDexReferences(dexEntry.getDexFile(), types, false);
      if (cache) dexCache.put(dexEntry, types, pool);
    }
    return pool;
  }

  @Nonnull
  public ReferencePool decode(@Nonnull DexBackedClassDef dexClass,
                              @Nonnull ReferenceTypes types) {
    if (types.hasNone()) return ReferencePool.emptyPool();
    ReferencePool pool = cache ? classCache.get(dexClass, types) : null;
    if (pool == null) {
      pool = decodeClassReferences(dexClass, types, false);
      if (cache) classCache.put(dexClass, types, pool);
    }
    return pool;
  }

  @Nonnull
  public ReferencePool decode(@Nonnull DexBackedMethod dexMethod,
                              @Nonnull ReferenceTypes types) {
    return decodeMethodReferences(dexMethod, types, false);
  }

  @Nonnull
  public static ReferencePool decodeFully(@Nonnull DexBackedDexFile dexFile) {
    return decodeDexReferences(dexFile, ReferenceTypes.all(), true);
  }

  @Nonnull
  public static ReferencePool decodeFully(@Nonnull DexBackedClassDef dexClass) {
    return decodeClassReferences(dexClass, ReferenceTypes.all(), true);
  }

  @Nonnull
  public static ReferencePool decodeFully(@Nonnull DexBackedField dexField) {
    if (dexField.getInitialValue() == null) return ReferencePool.emptyPool();
    RefsPoolBuffer buffer = new RefsPoolBuffer(ReferenceTypes.all());
    decodeFieldReferences(dexField, buffer);
    return buffer.getPool(true);
  }

  @Nonnull
  public static ReferencePool decodeFully(@Nonnull DexBackedMethod dexMethod) {
    return decodeMethodReferences(dexMethod, ReferenceTypes.all(), true);
  }

  public static Set<Long> decodeNumberLiterals(DexBackedClassDef dexClass) {
    Set<Long> pool = new HashSet<>();
    DexUtils.dexStaticFields(dexClass).forEach(f -> decodeNumberLiterals(f, pool));
    DexUtils.dexMethods(dexClass).forEach(m -> decodeNumberLiterals(m, pool));
    return pool;
  }

  public static Set<Long> decodeNumberLiterals(DexBackedMethod dexMethod) {
    Set<Long> pool = new HashSet<>();
    decodeNumberLiterals(dexMethod, pool);
    return pool;
  }

  private static ReferencePool decodeDexReferences(DexBackedDexFile dexFile,
                                                   ReferenceTypes types,
                                                   boolean resolve) {
    final RefsPoolBuffer buffer = new RefsPoolBuffer(types);
    if (types.hasString()) {
      HashSet<String> rm = new HashSet<>(dexFile.getTypeSection());
      for (FieldReference r: dexFile.getFieldSection()) rm.add(r.getName());
      for (MethodReference r: dexFile.getMethodSection()) rm.add(r.getName());
      dexFile.getStringSection().stream().filter(s -> !rm.contains(s)).forEach(buffer::add);
    }
    if (types.hasField()) dexFile.getFieldSection().forEach(buffer::add);
    if (types.hasMethod()) dexFile.getMethodSection().forEach(buffer::add);
    if (types.hasTypeDes()) dexFile.getTypeReferences().forEach(buffer::add);
    return buffer.getPool(resolve);
  }

  private static ReferencePool decodeClassReferences(DexBackedClassDef dexClass,
                                                     ReferenceTypes types,
                                                     boolean resolve) {
    final RefsPoolBuffer buffer = new RefsPoolBuffer(types);
    decodeClassFieldReferences(dexClass, types, buffer);
    getMethods(dexClass, types).forEach(m -> decodeMethodReferences(m, types, buffer));
    return buffer.getPool(resolve);
  }

  private static Iterable<DexBackedMethod> getMethods(DexBackedClassDef dexClass,
                                                      ReferenceTypes types) {
    switch (types.getScope()) {
      default: return Collections.emptyList();
      case ALL: return DexUtils.dexMethods(dexClass);
      case DIRECT: return DexUtils.dexDirectMethods(dexClass);
      case VIRTUAL: return DexUtils.dexVirtualMethods(dexClass);
    }
  }

  private static void decodeClassFieldReferences(DexBackedClassDef dexClass,
                                                 ReferenceTypes types,
                                                 RefsPoolBuffer pool) {
    if (types.hasString()) {
      DexUtils.dexStaticFields(dexClass).forEach(field -> decodeFieldReferences(field, pool));
    }
  }

  private static void decodeFieldReferences(DexBackedField dexField,
                                            RefsPoolBuffer pool) {
    EncodedValue value = dexField.getInitialValue();
    if (value != null && value.getValueType() == ValueType.STRING) {
      pool.add(new ImmutableStringReference(((StringEncodedValue)value).getValue()));
    }
  }

  private static ReferencePool decodeMethodReferences(DexBackedMethod dexMethod,
                                                      ReferenceTypes types,
                                                      boolean resolve) {
    RefsPoolBuffer buffer = new RefsPoolBuffer(types);
    decodeMethodReferences(dexMethod, types, buffer);
    return buffer.getPool(resolve);
  }

  private static void decodeMethodReferences(DexBackedMethod dexMethod,
                                             ReferenceTypes types,
                                             RefsPoolBuffer pool) {
    MethodImplementation implementation = dexMethod.getImplementation();
    if (implementation == null || types.hasNone()) return;
    for (Instruction instruction : implementation.getInstructions()) {
      if (instruction instanceof ReferenceInstruction) {
        decodeReference(((ReferenceInstruction) instruction).getReference(), types, pool);
        if (instruction instanceof DualReferenceInstruction) {
          decodeReference(((DualReferenceInstruction) instruction).getReference2(), types, pool);
        }
      }
    }
  }

  private static void decodeReference(Reference reference,
                                      ReferenceTypes types,
                                      RefsPoolBuffer pool) {
    try {
      reference.validateReference();
      if (reference instanceof StringReference) {
        if (types.hasString()) pool.add(((StringReference) reference));
      } else if (reference instanceof FieldReference) {
        if (types.hasField()) pool.add(((FieldReference) reference));
      } else if (reference instanceof MethodReference) {
        if (types.hasMethod()) pool.add(((MethodReference) reference));
      } else if (reference instanceof TypeReference) {
        if (types.hasTypeDes()) pool.add(((TypeReference) reference));
      }
    } catch (Reference.InvalidReferenceException e) {
      DexLog.w(e.getMessage());
    }
  }

  private static void decodeNumberLiterals(DexBackedMethod dexMethod, Set<Long> pool) {
    MethodImplementation implementation = dexMethod.getImplementation();
    if (implementation == null) return;
    for (Instruction instruction : implementation.getInstructions()) {
      if (instruction instanceof WideLiteralInstruction) {
        pool.add(((WideLiteralInstruction) instruction).getWideLiteral());
      }
    }
  }

  private static void decodeNumberLiterals(DexBackedField dexField, Set<Long> pool) {
    EncodedValue value = dexField.getInitialValue();
    if (value == null) return;
    switch (value.getValueType()) {
      case ValueType.SHORT:
        pool.add((long) ((ShortEncodedValue) value).getValue());
        break;
      case ValueType.INT:
        pool.add((long) ((IntEncodedValue) value).getValue());
        break;
      case ValueType.LONG:
        pool.add(((LongEncodedValue) value).getValue());
        break;
      case ValueType.FLOAT:
        pool.add((long) Float.floatToIntBits(((FloatEncodedValue) value).getValue()));
        break;
      case ValueType.DOUBLE:
        pool.add(Double.doubleToLongBits(((DoubleEncodedValue) value).getValue()));
        break;
    }
  }
}
