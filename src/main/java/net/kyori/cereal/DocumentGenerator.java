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
import net.kyori.lunar.exception.Exceptions;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

final class DocumentGenerator {
  private static final DefiningClassLoader CLASS_LOADER = new DefiningClassLoader(DocumentGenerator.class.getClassLoader());
  private final LoadingCache<Class<? extends Document>, Constructor<? extends Document>> cache = Caffeine.newBuilder()
    .initialCapacity(16)
    .build(face -> {
      final String name = BiteGenerator.concreteName(face);
      final byte[] bytes = this.generate(face, name);
      return (Constructor<? extends Document>) CLASS_LOADER.defineClass(name, bytes).getConstructors()[0];
    });
  private final DocumentRegistry registry;

  DocumentGenerator(final DocumentRegistry registry) {
    this.registry = registry;
  }

  private <I extends Document> byte[] generate(final Class<I> interfaceClass, final String concreteName) {
    return BiteGenerator.generate(interfaceClass, concreteName, this.registry.meta(interfaceClass));
  }

  <I extends Document, C extends I> C create(@NonNull final Class<I> interfaceClass, @NonNull final Object... args) {
    if(!Modifier.isInterface(interfaceClass.getModifiers())) {
      throw new IllegalArgumentException(String.format("Document class '%s' must be an interface", interfaceClass.getName()));
    }
    try {
      return this.create0(interfaceClass, args);
    } catch(final Exception e) {
      throw Exceptions.rethrow(e);
    }
  }

  private <I extends Document, C extends I> C create0(@NonNull final Class<I> interfaceClass, @NonNull final Object... args) throws IllegalAccessException, InstantiationException, InvocationTargetException {
    return (C) this.cache.get(interfaceClass).newInstance(args);
  }

  private static final class BiteGenerator<I extends Document> {
    private static final String PACKAGE = "generated";
    private static final String SUPER_NAME = "java/lang/Object";
    private final String interfaceName;
    private final DocumentMeta<I> meta;
    private final String concreteName;
    private final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

    private BiteGenerator(final Class<I> interfaceClass, final String concreteName, final DocumentMeta<I> meta) {
      this.interfaceName = internal(interfaceClass.getName());
      this.meta = meta;
      this.concreteName = internal(concreteName);
    }

    static <I extends Document> byte[] generate(final Class<I> interfaceClass, final String concreteName, final DocumentMeta<I> meta) {
      return new BiteGenerator<>(interfaceClass, concreteName, meta).generate();
    }

    private byte[] generate() {
      this.cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL, this.concreteName, null, SUPER_NAME, new String[]{this.interfaceName});

      this.generateFields();
      this.generateConstructor();
      this.generateGetters();

      return this.cw.toByteArray();
    }

    private void generateFields() {
      for(final Map.Entry<String, DocumentMeta.Field<?>> entry : this.meta.fields.entrySet()) {
        this.generateField(entry.getKey(), entry.getValue());
      }
    }

    private void generateField(final String name, final DocumentMeta.Field<?> field) {
      final FieldVisitor fv = this.cw.visitField(ACC_FINAL | ACC_PRIVATE, name, Type.getDescriptor(field.type()), null, null);
      fv.visitEnd();
    }

    private void generateConstructor() {
      final MethodVisitor mv = this.cw.visitMethod(ACC_PUBLIC, "<init>", constructorDescriptor(this.meta.fields.values().stream().map(DocumentMeta.Field::type).collect(Collectors.toList())), null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, SUPER_NAME, "<init>", "()V", false);
      int i = 0;
      for(final Map.Entry<String, DocumentMeta.Field<?>> entry : this.meta.fields.entrySet()) {
        i++;
        final String name = entry.getKey();
        final DocumentMeta.Field<?> field = entry.getValue();
        final Type type = Type.getType(Type.getDescriptor(field.type()));

        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), i);
        mv.visitFieldInsn(PUTFIELD, this.concreteName, name, Type.getDescriptor(field.type()));
      }
      mv.visitInsn(RETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    private void generateGetters() {
      for(final Map.Entry<String, DocumentMeta.Field<?>> entry : this.meta.fields.entrySet()) {
        this.generateGetter(entry.getKey(), entry.getValue());
      }
    }

    private void generateGetter(final String name, final DocumentMeta.Field<?> field) {
      final MethodVisitor mv = this.cw.visitMethod(ACC_PUBLIC, name, Type.getMethodDescriptor(field.method), null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, this.concreteName, name, Type.getDescriptor(field.type()));
      mv.visitInsn(Type.getType(Type.getDescriptor(field.type())).getOpcode(IRETURN));
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    private static String internal(final String string) {
      return string.replace('.', '/');
    }

    static String concreteName(final Class<?> source) {
      final String name = source.getName();
      return String.format("%s.%s.%s", source.getPackage().getName(), PACKAGE, name.substring(name.lastIndexOf('.') + 1).replace('$', '_') + "Impl" + randomId());
    }

    private static String randomId() {
      return UUID.randomUUID().toString().substring(26);
    }

    private static String constructorDescriptor(final Iterable<Class<?>> parameters) {
      final StringBuilder sb = new StringBuilder();
      sb.append('(');
      for(final Class<?> parameter : parameters) {
        sb.append(Type.getDescriptor(parameter));
      }
      return sb.append(")V").toString();
    }
  }

  // A class loader with a method exposed to define a class.
  private static final class DefiningClassLoader extends ClassLoader {
    private DefiningClassLoader(final ClassLoader parent) {
      super(parent);
    }

    <T> Class<T> defineClass(final String name, final byte[] bytes) {
      return (Class<T>) this.defineClass(name, bytes, 0, bytes.length);
    }
  }
}
