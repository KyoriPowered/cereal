/*
 * This file is part of cereal, licensed under the MIT License.
 *
 * Copyright (c) 2017 KyoriPowered
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.kyori.cereal;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.kyori.blizzard.NonNull;
import net.kyori.blizzard.Nullable;
import net.kyori.lunar.exception.Exceptions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

/**
 * Metadata describing a document.
 *
 * @param <D> the document type
 */
final class DocumentMeta<D extends Document> {
  /**
   * The document class.
   */
  @NonNull final Class<D> type;
  /**
   * A map of field names to field entries.
   */
  @NonNull final Map<String, Field<?>> fields;
  /**
   * The amount of fields.
   */
  private final int size;

  DocumentMeta(@NonNull final Class<D> type, @NonNull final Map<String, Field<?>> fields) {
    this.type = type;
    this.fields = fields;
    this.size = this.fields.size();
  }

  @NonNull
  Object[] deserialize(final JsonObject object, final JsonDeserializationContext context) {
    final Object[] fields = new Object[this.size];
    int i = 0;
    for(final Map.Entry<String, DocumentMeta.Field<?>> entry : this.fields.entrySet()) {
      fields[i++] = entry.getValue().deserialize(object.get(entry.getKey()), context);
    }
    return fields;
  }

  @NonNull
  JsonObject serialize(final Document document, final JsonSerializationContext context) {
    final JsonObject object = new JsonObject();
    for(final Map.Entry<String, DocumentMeta.Field<?>> entry : this.fields.entrySet()) {
      object.add(entry.getKey(), entry.getValue().serialize(document, context));
    }
    return object;
  }

  /*
   * While this class is called Field it actually represents a Method which is used to obtain type information
   * during deserialization, and the value of calling the method during serialization.
   */
  static abstract class Field<T> {
    final Method method;

    Field(final Method method) {
      this.method = method;
      this.method.setAccessible(true);
    }

    @NonNull
    abstract Class<?> type();

    @NonNull
    abstract Type genericType();

    @Nullable
    T get(final Object object) {
      try {
        return (T) this.method.invoke(object);
      } catch(final IllegalAccessException | InvocationTargetException e) {
        throw Exceptions.rethrow(e);
      }
    }

    @Nullable
    JsonElement serialize(@NonNull final Document document, @NonNull final JsonSerializationContext context) {
      return context.serialize(this.get(document), this.genericType());
    }

    @Nullable
    Object deserialize(@NonNull final JsonElement element, @NonNull final JsonDeserializationContext context) {
      return context.deserialize(element, this.genericType());
    }

    static <T> Field<T> create(final Method method) {
      final Type grt = method.getGenericReturnType();
      if(grt instanceof ParameterizedType && ((ParameterizedType) grt).getRawType().equals(Optional.class)) {
        return new OptionalField<>(method);
      }
      return new NormalField<>(method);
    }
  }

  static class NormalField<T> extends Field<T> {
    private final Class<?> type;
    private final Type genericType;

    NormalField(final Method method) {
      super(method);
      this.type = this.method.getReturnType();
      this.genericType = this.method.getGenericReturnType();
    }

    @NonNull
    @Override
    Class<?> type() {
      return this.type;
    }

    @NonNull
    @Override
    Type genericType() {
      return this.genericType;
    }
  }

  static class OptionalField<T> extends Field<T> {
    private final Type genericType;

    OptionalField(final Method method) {
      super(method);
      this.genericType = ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments()[0];
    }

    @NonNull
    @Override
    Class<?> type() {
      return Optional.class;
    }

    @NonNull
    @Override
    Type genericType() {
      return this.genericType;
    }

    @Nullable
    @Override
    T get(final Object object) {
      return ((Optional<T>) super.get(object)).orElse(null);
    }

    @Nullable
    @Override
    Object deserialize(@NonNull final JsonElement element, @NonNull final JsonDeserializationContext context) {
      return Optional.ofNullable(context.deserialize(element, this.genericType()));
    }
  }
}
