/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.TypeParameterMatcher;

/**
 * A Codec for on-the-fly encoding/decoding of bytes to messages and vise-versa.
 *
 * This can be thought of as a combination of {@link ByteToMessageDecoder} and {@link MessageToByteEncoder}.
 *
 * Be aware that sub-classes of {@link ByteToMessageCodec} <strong>MUST NOT</strong>
 * annotated with {@link @Sharable}.
 */
public abstract class ByteToMessageCodec<I> extends ChannelHandlerAdapter {

    private final TypeParameterMatcher outboundMsgMatcher;
    private final MessageToByteEncoder<I> encoder;

    private final ByteToMessageDecoder decoder = new ByteToMessageDecoder() {
        @Override
        public void decode(ChannelHandlerContext ctx, Buffer in) throws Exception {
            ByteToMessageCodec.this.decode(ctx, in);
        }

        @Override
        protected void decodeLast(ChannelHandlerContext ctx, Buffer in) throws Exception {
            ByteToMessageCodec.this.decodeLast(ctx, in);
        }
    };

    /**
     * see {@link #ByteToMessageCodec(BufferAllocator)} with {@code true} as boolean parameter.
     */
    protected ByteToMessageCodec() {
        this((BufferAllocator) null);
    }

    /**
     * see {@link #ByteToMessageCodec(Class, BufferAllocator)} with {@code true} as boolean value.
     */
    protected ByteToMessageCodec(Class<? extends I> outboundMessageType) {
        this(outboundMessageType, null);
    }

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter of the class.
     *
     * @param allocator             The allocator to use for allocating buffers.
     *                              If {@code null}, the channel context allocator will be used.
     */
    protected ByteToMessageCodec(BufferAllocator allocator) {
        ensureNotSharable();
        outboundMsgMatcher = TypeParameterMatcher.find(this, ByteToMessageCodec.class, "I");
        encoder = new Encoder(allocator);
    }

    /**
     * Create a new instance
     *
     * @param outboundMessageType   The type of messages to match.
     * @param allocator             The allocator to use for allocating buffers.
     *                              If {@code null}, the channel context allocator will be used.
     */
    protected ByteToMessageCodec(Class<? extends I> outboundMessageType, BufferAllocator allocator) {
        ensureNotSharable();
        outboundMsgMatcher = TypeParameterMatcher.get(outboundMessageType);
        encoder = new Encoder(allocator);
    }

    /**
     * Returns {@code true} if and only if the specified message can be encoded by this codec.
     *
     * @param msg the message
     */
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return outboundMsgMatcher.match(msg);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        decoder.channelRead(ctx, msg);
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        return encoder.write(ctx, msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        decoder.channelReadComplete(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        decoder.channelInactive(ctx);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        try {
            decoder.handlerAdded(ctx);
        } finally {
            encoder.handlerAdded(ctx);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        try {
            decoder.handlerRemoved(ctx);
        } finally {
            encoder.handlerRemoved(ctx);
        }
    }

    /**
     * @see MessageToByteEncoder#encode(ChannelHandlerContext, Object, Buffer)
     */
    protected abstract void encode(ChannelHandlerContext ctx, I msg, Buffer out) throws Exception;

    /**
     * @see ByteToMessageDecoder#decode(ChannelHandlerContext, Buffer)
     */
    protected abstract void decode(ChannelHandlerContext ctx, Buffer in) throws Exception;

    /**
     * @see ByteToMessageDecoder#decodeLast(ChannelHandlerContext, Buffer)
     */
    protected void decodeLast(ChannelHandlerContext ctx, Buffer in) throws Exception {
        if (in.readableBytes() > 0) {
            // Only call decode() if there is something left in the buffer to decode.
            // See https://github.com/netty/netty/issues/4386
            decode(ctx, in);
        }
    }

    private final class Encoder extends MessageToByteEncoder<I> {
        private final BufferAllocator allocator;

        Encoder(BufferAllocator allocator) {
            this.allocator = allocator;
        }

        @Override
        public boolean acceptOutboundMessage(Object msg) throws Exception {
            return ByteToMessageCodec.this.acceptOutboundMessage(msg);
        }

        @Override
        protected Buffer allocateBuffer(ChannelHandlerContext ctx, I msg) throws Exception {
            BufferAllocator alloc = allocator != null? allocator : ctx.bufferAllocator();
            return alloc.allocate(256);
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, I msg, Buffer out) throws Exception {
            ByteToMessageCodec.this.encode(ctx, msg, out);
        }
    }
}
