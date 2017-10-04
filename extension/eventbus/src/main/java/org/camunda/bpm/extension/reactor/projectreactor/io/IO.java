/*
 * Copyright (c) 2011-2015 Pivotal Software Inc, All Rights Reserved.
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
package org.camunda.bpm.extension.reactor.projectreactor.io;

import org.camunda.bpm.extension.reactor.projectreactor.core.reactivestreams.PublisherFactory;
import org.camunda.bpm.extension.reactor.projectreactor.core.reactivestreams.SubscriberWithContext;
import org.camunda.bpm.extension.reactor.projectreactor.core.support.ReactorFatalException;
import org.camunda.bpm.extension.reactor.projectreactor.io.buffer.Buffer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A factory for Reactive basic IO operations such as File read/write, Byte read and Codec decoding.
 *
 * @author Stephane Maldini
 * @since 2.0.4
 */
public final class IO {

  private IO() {
  }

  /**
   * Transform a {@link ReadableByteChannel} into a {@link Publisher} of {@link Buffer} with a max chunk size of
   * {@link Buffer.SMALL_BUFFER_SIZE}.
   * <p>
   * Complete when channel read is negative. The read sequence is unique per subscriber.
   *
   * @param channel The Readable Channel to publish
   * @return a Publisher of Buffer values
   */
  public static Publisher<Buffer> read(final ReadableByteChannel channel) {
    return read(channel, -1);
  }

  /**
   * Transform a {@link ReadableByteChannel} into a {@link Publisher} of {@link Buffer} with a max chunk size of
   * {@code chunkSize}.
   * Complete when channel read is negative. The read sequence is unique per subscriber.
   *
   * @param channel The Readable Channel to publish
   * @return a Publisher of Buffer values
   */
  public static Publisher<Buffer> read(final ReadableByteChannel channel, int chunkSize) {
    return PublisherFactory.forEach(
      chunkSize < 0 ? defaultChannelReadConsumer : new ChannelReadConsumer(chunkSize),
      new Function<Subscriber<? super Buffer>, ReadableByteChannel>() {
        @Override
        public ReadableByteChannel apply(Subscriber<? super Buffer> subscriber) {
          return channel;
        }
      },
      channelCloseConsumer
    );
  }

  /**
   * Read bytes as {@link Buffer} from file specified by the {@link Path} argument with a max chunk size of
   * {@link Buffer.SMALL_BUFFER_SIZE}.
   * <p>
   * Complete when channel read is negative. The read sequence is unique per subscriber.
   *
   * @param path the {@link Path} locating the file to read
   * @return a Publisher of Buffer values read from file sequentially
   */
  public static Publisher<Buffer> readFile(Path path) {
    return readFile(path.toAbsolutePath().toString(), -1);
  }


  /**
   * Read bytes as {@link Buffer} from file specified by the {@link Path} argument with a max {@code chunkSize}
   * <p>
   * Complete when channel read is negative. The read sequence is unique per subscriber.
   *
   * @param path the {@link Path} locating the file to read
   * @return a Publisher of Buffer values read from file sequentially
   */
  public static Publisher<Buffer> readFile(Path path, int chunkSize) {
    return readFile(path.toAbsolutePath().toString(), chunkSize);
  }

  /**
   * Read bytes as {@link Buffer} from file specified by the {@link Path} argument with a max chunk size of
   * {@link Buffer.SMALL_BUFFER_SIZE}.
   * <p>
   * Complete when channel read is negative. The read sequence is unique per subscriber.
   *
   * @param path the absolute String path to the read file
   * @return a Publisher of Buffer values read from file sequentially
   */
  public static Publisher<Buffer> readFile(final String path) {
    return readFile(path, -1);
  }

  /**
   * Read bytes as {@link Buffer} from file specified by the {@link Path} argument with a max {@code chunkSize}
   * <p>
   * Complete when channel read is negative. The read sequence is unique per subscriber.
   *
   * @param path the absolute String path to the read file
   * @return a Publisher of Buffer values read from file sequentially
   */
  public static Publisher<Buffer> readFile(final String path, int chunkSize) {
    return PublisherFactory.forEach(
      chunkSize < 0 ? defaultChannelReadConsumer : new ChannelReadConsumer(chunkSize),
      new Function<Subscriber<? super Buffer>, ReadableByteChannel>() {
        @Override
        public ReadableByteChannel apply(Subscriber<? super Buffer> subscriber) {
          try {
            RandomAccessFile file = new RandomAccessFile(path, "r");
            return new FileContext(file);
          } catch (FileNotFoundException e) {
            throw ReactorFatalException.create(e);
          }
        }
      },
      channelCloseConsumer);
  }

  private static final ChannelCloseConsumer channelCloseConsumer = new ChannelCloseConsumer();
  private static final ChannelReadConsumer defaultChannelReadConsumer = new ChannelReadConsumer(Buffer
    .SMALL_BUFFER_SIZE);

  /**
   * A read access to the source file
   */
  public static final class FileContext implements ReadableByteChannel {
    private final RandomAccessFile file;
    private final ReadableByteChannel channel;

    public FileContext(RandomAccessFile file) {
      this.file = file;
      this.channel = file.getChannel();
    }

    public RandomAccessFile file() {
      return file;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
      return channel.read(dst);
    }

    @Override
    public boolean isOpen() {
      return channel.isOpen();
    }

    @Override
    public void close() throws IOException {
      channel.close();
    }
  }

  private static final class ChannelReadConsumer implements Consumer<SubscriberWithContext<Buffer,
    ReadableByteChannel>> {

    private final int bufferSize;

    public ChannelReadConsumer(int bufferSize) {
      this.bufferSize = bufferSize;
    }

    @Override
    public void accept(SubscriberWithContext<Buffer, ReadableByteChannel> sub) {
      try {
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        int read;
        if ((read = sub.context().read(buffer)) > 0) {
          buffer.flip();
          sub.onNext(new Buffer(buffer).limit(read));
        } else {
          sub.onComplete();
        }
      } catch (IOException e) {
        sub.onError(e);
      }
    }
  }

  private static final class ChannelCloseConsumer implements Consumer<ReadableByteChannel> {
    @Override
    public void accept(ReadableByteChannel channel) {
      try {
        if (channel != null) {
          channel.close();
        }
      } catch (IOException ioe) {
        throw new IllegalStateException(ioe);
      }
    }
  }


}
