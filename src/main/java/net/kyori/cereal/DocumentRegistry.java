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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import net.kyori.blizzard.NonNull;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * A document registry maintains a mapping of document classes to document metadata.
 */
final class DocumentRegistry {
  private final LoadingCache<Class<? extends Document>, DocumentMeta<? extends Document>> meta = Caffeine.newBuilder()
    .build(type -> {
      final Map<String, DocumentMeta.Field<?>> fields = new TreeMap<>();
      for(final Method method : type.getMethods()) {
        if(!Document.class.isAssignableFrom(method.getDeclaringClass())) {
          continue;
        }
        fields.put(method.getName(), DocumentMeta.Field.create(method));
      }
      return new DocumentMeta<>(type, new LinkedHashMap<>(fields));
    });

  /**
   * Gets the document metadata for the specified document class.
   *
   * @param type the document class
   * @param <D> the document type
   * @return the document metadata
   */
  @NonNull
  <D extends Document> DocumentMeta<D> meta(@NonNull final Class<D> type) {
    return (DocumentMeta<D>) this.meta.get(type);
  }
}
