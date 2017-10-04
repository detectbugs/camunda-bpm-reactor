/*
 * Copyright (c) 2011-2014 Pivotal Software, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.camunda.bpm.extension.reactor.projectreactor.fn;


import io.vavr.Tuple;
import io.vavr.Tuple2;

import java.lang.reflect.Constructor;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Helper methods to provide syntax sugar for working with functional components in Reactor.
 *
 * @author Jon Brisbin
 * @author Stephane Maldini
 * @author Andy Wilkinson
 */
@Deprecated
public abstract class Functions {

  /**
   * Chain a set of {@link Consumer Consumers} together into a single {@link Consumer}.
   *
   * @param consumers the {@link Consumer Consumers} to chain together
   * @param <T>       type of the event handled by the {@link Consumer}
   * @return a new {@link Consumer}
   */
  @SafeVarargs
  public static <T> Consumer<T> chain(Consumer<T>... consumers) {
    final AtomicReference<Consumer<T>> composition = new AtomicReference<Consumer<T>>();
    for (final Consumer<T> next : consumers) {
      if (null == composition.get()) {
        composition.set(next);
      } else {
        composition.set(new Consumer<T>() {
          final Consumer<T> prev = composition.get();

          public void accept(T t) {
            prev.accept(t);
            next.accept(t);
          }
        });
      }
    }
    return composition.get();
  }

  /**
   * Wrap the given {@link java.util.concurrent.Callable} and compose a new {@link Function}.
   *
   * @param c The {@link java.util.concurrent.Callable}.
   * @return An {@link Consumer} that executes the {@link java.util.concurrent.Callable}.
   */
  public static <T, V> Function<T, V> function(final Callable<V> c) {
    return o -> {
      try {
        return c.call();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    };
  }

  /**
   * Wrap a given {@link Function} that applies transformation to a Tuple2 into a PairFunction.
   *
   * @param function tuple2 function to wrap into a PairFunction
   * @return A {@link BiFunction} that delegates to a new applied tuple if called with 2 arguments an to the applied tuple itself.
   */
  public static <LEFT, RIGHT, V> BiFunction<LEFT, RIGHT, V> pairFrom(final Function<Tuple2<LEFT, RIGHT>, V> function) {
    return (left, right) -> function.apply(Tuple.of(left, right));
  }

  /**
   * Wrap a given {@link BiFunction} that applies transformation to a Tuple2 into a Function Tuple2.
   *
   * @param pairFunction PairFunction to wrap into a Function
   * @return A {@link BiFunction} that delegates to a new applied tuple if called with 2 arguments an to the applied tuple itself.
   */
  public static <LEFT, RIGHT, V> Function<Tuple2<LEFT, RIGHT>, V> functionFrom(final BiFunction<LEFT, RIGHT, V> pairFunction) {
    return tuple2 -> pairFunction.apply(tuple2._1, tuple2._2);
  }

  /**
   * Wrap the given {@link Runnable} and compose a new {@link Consumer}.
   *
   * @param r The {@link Runnable}.
   * @return An {@link Consumer} that executes the {@link Runnable}.
   */
  public static <T> Consumer<T> consumer(final Runnable r) {
    return t -> r.run();
  }

  /**
   * Creates a {@code Supplier} that will always return the given {@code value}.
   *
   * @param value the value to be supplied
   * @return the supplier for the value
   */
  public static <T> Supplier<T> supplier(final T value) {
    return () -> value;
  }

  /**
   * Creates a {@code Supplier} that will return a new instance of {@code type} each time
   * it's called.
   *
   * @param type The type to create
   * @return The supplier that will create instances
   * @throws IllegalArgumentException if {@code type} does not have a zero-args constructor
   */
  public static <T> Supplier<T> supplier(final Class<T> type) {
    try {
      final Constructor<T> ctor = type.getConstructor();
      return () -> {
        try {
          return ctor.newInstance();
        } catch (Exception e) {
          throw new IllegalStateException(e.getMessage(), e);
        }
      };
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    }
  }

  /**
   * Creates a {@code Supplier} that will {@link Callable#call call} the {@code callable}
   * each time it's asked for a value.
   *
   * @param callable The {@link Callable}.
   * @return A {@link Supplier} that executes the {@link Callable}.
   */
  public static <T> Supplier<T> supplier(final Callable<T> callable) {
    return () -> {
      try {
        return callable.call();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    };
  }

  /**
   * Creates a {@code Supplier} that will {@link Future#get get} its value from the
   * {@code future} each time it's asked for a value.
   *
   * @param future The future to get values from
   * @return A {@link Supplier} that gets its values from the Future
   */
  public static <T> Supplier<T> supplier(final Future<T> future) {
    return () -> {
      try {
        return future.get();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    };
  }

}
