/*
 * Copyright (c) 2011-2015 Pivotal Software Inc., Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.extension.reactor.projectreactor.core.internal;

/**
 * @author Stephane Maldini
 */

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Borrowed from Netty project which itself borrows from JCTools and various other projects.
 * <p>
 * The {@see github.com/netty/netty/blob/master/common/src/main/java/io/netty/util/internal/PlatformDependent.java}
 * operations which requires access to {@code sun.misc.*}.
 */
final class PlatformDependent0 {

  private static final Unsafe UNSAFE;


  static {
    ByteBuffer direct = ByteBuffer.allocateDirect(1);
    Field addressField;
    try {
      addressField = Buffer.class.getDeclaredField("address");
      addressField.setAccessible(true);
      if (addressField.getLong(ByteBuffer.allocate(1)) != 0) {
        // A heap buffer must have 0 address.
        addressField = null;
      } else {
        if (addressField.getLong(direct) == 0) {
          // A direct buffer must have non-zero address.
          addressField = null;
        }
      }
    } catch (Throwable t) {
      // Failed to access the address field.
      addressField = null;
    }
    Unsafe unsafe;
    if (addressField != null) {
      try {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        unsafe = (Unsafe) unsafeField.get(null);

        // Ensure the unsafe supports all necessary methods to work around the mistake in the latest OpenJDK.
        // https://github.com/netty/netty/issues/1061
        // http://www.mail-archive.com/jdk6-dev@openjdk.java.net/msg00698.html
        try {
          if (unsafe != null) {
            unsafe.getClass().getDeclaredMethod(
              "copyMemory", Object.class, long.class, Object.class, long.class, long.class);
          }
        } catch (NoSuchMethodError | NoSuchMethodException t) {
          throw t;
        }
      } catch (Throwable cause) {
        // Unsafe.copyMemory(Object, long, Object, long, long) unavailable.
        unsafe = null;
      }
    } else {
      // If we cannot access the address of a direct buffer, there's no point of using unsafe.
      // Let's just pretend unsafe is unavailable for overall simplicity.
      unsafe = null;
    }

    UNSAFE = unsafe;
  }

  static boolean hasUnsafe() {
    return UNSAFE != null;
  }


  static <U, W> AtomicReferenceFieldUpdater<U, W> newAtomicReferenceFieldUpdater(
    Class<U> tclass, String fieldName) throws Exception {
    return new UnsafeAtomicReferenceFieldUpdater<U, W>(UNSAFE, tclass, fieldName);
  }

  static ClassLoader getSystemClassLoader() {
    if (System.getSecurityManager() == null) {
      return ClassLoader.getSystemClassLoader();
    } else {
      return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
        @Override
        public ClassLoader run() {
          return ClassLoader.getSystemClassLoader();
        }
      });
    }
  }

  private PlatformDependent0() {
  }

}
