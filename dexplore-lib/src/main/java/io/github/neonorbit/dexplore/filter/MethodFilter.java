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

package io.github.neonorbit.dexplore.filter;

import io.github.neonorbit.dexplore.DexDecoder;
import io.github.neonorbit.dexplore.LazyDecoder;
import io.github.neonorbit.dexplore.exception.AbortException;
import io.github.neonorbit.dexplore.util.DexUtils;
import io.github.neonorbit.dexplore.iface.Internal;
import io.github.neonorbit.dexplore.util.Utils;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedMethod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A filter used to select dex methods of interest.
 * <p><br>
 *   Note: The filter will match if and only if all the specified conditions are satisfied.
 * </p>
 *
 * @author NeonOrbit
 * @since 1.0.0
 */
public final class MethodFilter extends BaseFilter<DexBackedMethod> {
  private static final int NEG = -1;

  /** A {@code MethodFilter} instance that matches all dex methods. */
  public static final MethodFilter MATCH_ALL = new MethodFilter(builder());

  private final int flag;
  private final int skipFlag;
  private final int paramSize;
  private final String returnType;
  private final Set<String> methodNames;
  private final List<String> parameters;
  private final Set<String> annotations;
  private final Set<String> annotValues;
  private final Set<Long> numLiterals;

  private MethodFilter(Builder builder) {
    super(builder, Utils.isSingle(builder.methodNames) &&
         (builder.parameters != null || builder.paramSize == 0)
    );
    this.flag = builder.flag;
    this.skipFlag = builder.skipFlag;
    this.paramSize = builder.paramSize;
    this.parameters = builder.parameters;
    this.returnType = builder.returnType;
    this.methodNames = builder.methodNames;
    this.annotations = builder.annotations;
    this.annotValues = builder.annotValues;
    this.numLiterals = builder.numLiterals;
  }

  @Internal
  @Override
  public boolean verify(@Nonnull DexBackedMethod dexMethod,
                        @Nonnull LazyDecoder<DexBackedMethod> decoder) {
    if (this == MATCH_ALL) return true;
    if (!checkMethodSignature(dexMethod)) return false;
    boolean result = (
            (flag == NEG || (dexMethod.accessFlags & flag) == flag) &&
            (skipFlag == NEG || (dexMethod.accessFlags & skipFlag) == 0) &&
            (returnType == null || returnType.equals(dexMethod.getReturnType())) &&
            (annotations == null || FilterUtils.containsAllAnnotations(dexMethod, annotations)) &&
            (annotValues == null || FilterUtils.containsAllAnnotationValues(dexMethod, annotValues)) &&
            (numLiterals == null || DexDecoder.decodeNumberLiterals(dexMethod).containsAll(numLiterals)) &&
            super.verify(dexMethod, decoder)
    );
    if (unique && !result) {
      throw new AbortException("Method found but the filter didn't match");
    }
    return result;
  }

  private boolean checkMethodSignature(DexBackedMethod dexMethod) {
    if (methodNames != null && !methodNames.contains(dexMethod.getName())) {
      return false;
    } else if (parameters != null) {
      List<String> list = dexMethod.getParameterTypes();
      return parameters.size() == list.size() && list.equals(parameters);
    }
    return paramSize < 0 || dexMethod.getParameterTypes().size() == paramSize;
  }

  /**
   * This is equivalent to:
   * <blockquote><pre>
   *   new MethodFilter.Builder()
   *                   .{@link MethodFilter.Builder#setMethodNames(String...)
   *                           setMethodNames(method)}
   *                   .{@link MethodFilter.Builder#setParamList(List)
   *                           setParamList(params)}
   *                   .build();
   * </pre></blockquote>
   *
   * @param method method name
   * @param params {@linkplain Class#getName() full names} of method parameter types
   * @return a {@code MethodFilter} instance
   */
  public static MethodFilter ofMethod(@Nonnull String method,
                                      @Nonnull List<String> params) {
    return builder().setMethodNames(method).setParamList(params).build();
  }

  /**
   * This is equivalent to:
   * <blockquote><pre>
   *   new MethodFilter.Builder()
   *                   .{@link MethodFilter.Builder#setMethodNames(String...)
   *                           setMethodNames(method)}
   *                   .{@link MethodFilter.Builder#setParamList(List)
   *                           setParamList(Collections.emptyList())}
   *                   .build();
   * </pre></blockquote>
   *
   * @param method method name
   * @return a {@code MethodFilter} instance
   */
  public static MethodFilter ofMethod(@Nonnull String method) {
    return ofMethod(method, Collections.emptyList());
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder extends BaseFilter.Builder<Builder, MethodFilter> {
    private int flag = NEG;
    private int skipFlag = NEG;
    private int paramSize = NEG;
    private String returnType;
    private Set<String> methodNames;
    private List<String> parameters;
    private Set<String> annotations;
    private Set<String> annotValues;
    private Set<Long> numLiterals;

    public Builder() {}

    private Builder(MethodFilter instance) {
      super(instance);
      this.flag = instance.flag;
      this.skipFlag = instance.skipFlag;
      this.paramSize = instance.paramSize;
      this.parameters = instance.parameters;
      this.returnType = instance.returnType;
      this.methodNames = instance.methodNames;
      this.annotations = instance.annotations;
      this.annotValues = instance.annotValues;
      this.numLiterals = instance.numLiterals;
    }

    @Override
    protected boolean isDefault() {
      return super.isDefault()    &&
              flag        == NEG  &&
              skipFlag    == NEG  &&
              paramSize   == NEG  &&
              parameters  == null &&
              returnType  == null &&
              methodNames == null &&
              annotations == null &&
              annotValues == null &&
              numLiterals == null;
    }

    @Override
    protected Builder getThis() {
      return this;
    }

    @Override
    public MethodFilter build() {
      return isDefault() ? MATCH_ALL : new MethodFilter(this);
    }

    /**
     * Add a condition to the filter to match methods that match with any of the specified method names.
     *
     * @param names method names
     * @return {@code this} builder
     */
    public Builder setMethodNames(@Nonnull String... names) {
      List<String> list = Utils.nonNullList(names);
      this.methodNames = list.isEmpty() ? null : Utils.optimizedSet(list);
      return this;
    }

    /**
     * Add a condition to the filter to match methods with the specified method modifiers.
     * <br>
     * Examples:
     *    <blockquote> setModifiers({@link Modifier#PUBLIC}) </blockquote>
     * Use {@code |} operator to set multiple modifiers:
     *    <blockquote> setModifiers({@link Modifier#PUBLIC} | {@link Modifier#STATIC}) </blockquote>
     *
     * @param modifiers method {@link Method#getModifiers() modifiers}, or -1 to reset
     * @return {@code this} builder
     * @see #skipModifiers(int)
     */
    public Builder setModifiers(int modifiers) {
      this.flag = modifiers;
      return this;
    }

    /**
     * Methods with the specified method modifiers will be skipped.
     *
     * @param modifiers method {@link Method#getModifiers() modifiers}, or -1 to reset
     * @return {@code this} builder
     * @see #setModifiers(int)
     */
    public Builder skipModifiers(int modifiers) {
      this.skipFlag = modifiers;
      return this;
    }

    /**
     * Add a condition to the filter to match methods with the specified method return type.
     *
     * @param returnType {@linkplain Class#getName() full name} of method return type
     * @return {@code this} builder
     */
    public Builder setReturnType(@Nullable String returnType) {
      this.returnType = returnType == null ? null : DexUtils.javaToDexTypeName(returnType);
      return this;
    }

    /**
     * Add a condition to the filter to match methods with the specified method parameter size.
     *
     * @param size number of method parameters, or -1 to reset
     * @return {@code this} builder
     */
    public Builder setParamSize(int size) {
      this.paramSize = size;
      return this;
    }

    /**
     * Add a condition to the filter to match methods with the specified parameter list.
     * <p>
     *   Note: Parameter list must match exactly. <br>
     *   Note: Pass an empty list to match methods that have no parameters.
     * </p>
     *
     * @param params {@linkplain Class#getName() full names} of method parameter types
     * @return {@code this} builder
     */
    public Builder setParamList(@Nullable List<String> params) {
      this.parameters = params == null ? null : Utils.optimizedList(DexUtils.javaToDexTypeName(params));
      return this;
    }

    /**
     * Add a condition to the filter to match methods that contain all the specified annotations.
     *
     * @param annotations {@linkplain Class#getName() full names} of annotation classes
     * @return {@code this} builder
     * @see #containsAnnotationValues(String...)
     */
    public Builder containsAnnotations(@Nonnull String... annotations) {
      List<String> list = DexUtils.javaToDexTypeName(Utils.nonNullList(annotations));
      this.annotations = list.isEmpty() ? null : Utils.optimizedSet(list);
      return this;
    }

    /**
     * Add a condition to the filter to match methods that contain all the specified annotation values.
     *
     * <p>Currently supports only string and type values.</p>
     * <pre>
     *   STRING Values: @SomeAnnot("string") @AnotherAnnot({"string1", "string2"})
     *   Example: filter.containsAnnotationValues("string", "string1")
     *
     *   TYPE Values: @Annot(Runnable.class), @Annot(Thread.class)
     *   Example: filter.containsAnnotationValues("java.lang.Runnable", "java.lang.Thread")
     * </pre>
     *
     * @param annotationValues annotation values
     * @return {@code this} builder
     * @see #containsAnnotations(String...)
     */
    public Builder containsAnnotationValues(@Nonnull String... annotationValues) {
      List<String> list = Utils.nonNullList(annotationValues);
      this.annotValues = list.isEmpty() ? null : Utils.optimizedSet(list);
      return this;
    }

    /**
     * Add a condition to the filter to match methods that contain all the specified numbers.
     * <p>Note: Each float value must end with an 'f' character.</p>
     *
     * @param numbers list of numbers to match
     * @return {@code this} builder
     */
    public Builder setNumbers(@Nonnull Number... numbers) {
      Set<Long> literals = Utils.nonNullList(numbers).stream().map(number ->
              number instanceof Float ? Float.floatToIntBits((Float) number) :
                      number instanceof Double ? Double.doubleToLongBits((Double) number) :
                              number.longValue()
      ).collect(Collectors.toSet());
      this.numLiterals = literals.isEmpty() ? null : Utils.optimizedSet(literals);
      return this;
    }
  }
}
