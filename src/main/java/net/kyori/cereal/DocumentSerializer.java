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

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * A serializer used for all {@link Document}s.
 */
public final class DocumentSerializer implements JsonDeserializer<Document>, JsonSerializer<Document> {
  private final DocumentRegistry registry = new DocumentRegistry();
  private final DocumentGenerator generator = new DocumentGenerator(this.registry);

  @Override
  public Document deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context) throws JsonParseException {
    final DocumentMeta<? extends Document> meta = this.registry.meta(TypeToken.of(typeOfT).getRawType().asSubclass(Document.class));
    final Object[] fields = meta.deserialize((JsonObject) json, context);
    return this.generator.create(meta.type, fields);
  }

  @Override
  public JsonElement serialize(final Document src, final Type typeOfSrc, final JsonSerializationContext context) {
    final DocumentMeta<? extends Document> meta = this.registry.meta(src.getClass());
    return meta.serialize(src, context);
  }
}
