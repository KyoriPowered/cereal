/*
 * This file is part of cereal, licensed under the MIT License.
 *
 * Copyright (c) 2017-2020 KyoriPowered
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

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CerealTest {
  private static final Gson GSON = new GsonBuilder()
    .registerTypeHierarchyAdapter(Document.class, DocumentSerializer.both())
    .create();

  @Test
  void testStandard() {
    final Entity source = new Entity() {
      @Override
      public int id() {
        return 0;
      }

      @Override
      public String name() {
        return "test";
      }

      @Override
      public Optional<String> value() {
        return Optional.of("bar");
      }

      @Override
      public Map<String, List<String>> strings() {
        return ImmutableMap.of("foo", Arrays.asList("bar", "baz"));
      }
    };
    final String json = GSON.toJson(source);
    final Entity target = GSON.fromJson(json, Entity.class);

    assertEquals(source.id(), target.id());
    assertEquals(source.name(), target.name());
    assertEquals(source.value(), target.value());
    assertEquals(source.strings(), target.strings());
  }

  @Test
  void testWithExclude() {
    final ThingWithDefault source = () -> 42;
    final String json = GSON.toJson(source);
    assertFalse(json.contains("bar"));
    assertTrue(json.contains("razz"));
    final ThingWithDefault target = GSON.fromJson(json, ThingWithDefault.class);

    assertEquals(source.thing(), target.thing());
    assertEquals(source.foo(), target.foo());
    assertEquals(source.baz(), target.baz());
  }

  public interface Entity extends Document {
    int id();
    String name();
    Optional<String> value();
    Map<String, List<String>> strings();
  }

  public interface ThingWithDefault extends Document {
    int thing();
    /* default is excluded by default */ default String foo() { return "bar"; }
    @Exclude(false) default String baz() { return "razz"; }
  }
}
