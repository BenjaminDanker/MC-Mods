package com.silver.viewextend;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.protocol.Packet;

/** Observes ViewExtend chunk bytes at the normal Minecraft encoder/compressor boundaries. */
final class ViewExtendNetworkMetrics {
    private static final String MARKER = "viewextend_packet_marker";
    private static final String LOGICAL = "viewextend_logical_bytes";
    private static final String COMPRESSED = "viewextend_compressed_bytes";
    private static final String WIRE = "viewextend_wire_bytes";
    private static final int HISTOGRAM_BUCKETS = 32;

    private final LongAdder logicalBytes = new LongAdder();
    private final LongAdder compressedBytes = new LongAdder();
    private final LongAdder wireBytes = new LongAdder();
    private final LongAdder packets = new LongAdder();
    private final LongAdder blockStateBytes = new LongAdder();
    private final LongAdder biomeBytes = new LongAdder();
    private final LongAdder heightmapBytes = new LongAdder();
    private final LongAdder skyLightBytes = new LongAdder();
    private final LongAdder blockLightBytes = new LongAdder();
    private final LongAdder lightMaskBytes = new LongAdder();
    private final LongAdder protocolBytes = new LongAdder();
    private final LongAdder uncategorizedPackets = new LongAdder();
    private final LongAdder droppedObservations = new LongAdder();
    private final AtomicLong[] logicalHistogram = histogram();
    private final AtomicLong compressionThreshold = new AtomicLong(-1);

    void observe(Channel channel, Packet<?> packet, VisualChunkPackets.PacketComposition composition) {
        if (channel == null || !channel.isOpen()) return;
        channel.eventLoop().execute(() -> {
            if (!channel.isOpen()) return;
            ChannelPipeline pipeline = channel.pipeline();
            Tracker tracker = pipeline.get(MARKER) instanceof Tracker existing ? existing : install(pipeline);
            if (tracker == null) {
                droppedObservations.increment();
                return;
            }
            if (pipeline.get("compress") instanceof CompressionEncoder encoder) {
                compressionThreshold.set(encoder.getThreshold());
            } else {
                compressionThreshold.set(-1);
            }
            tracker.register(packet, composition);
        });
    }

    private Tracker install(ChannelPipeline pipeline) {
        if (pipeline.get("encoder") == null || pipeline.get("prepender") == null) return null;
        Tracker tracker = new Tracker(this);
        pipeline.addAfter("encoder", MARKER, tracker);
        if (pipeline.get("compress") != null) {
            pipeline.addAfter("compress", LOGICAL, tracker.logicalHandler);
        } else {
            pipeline.addAfter("prepender", LOGICAL, tracker.logicalHandler);
        }
        pipeline.addAfter("prepender", COMPRESSED, tracker.compressedHandler);
        pipeline.addBefore("prepender", WIRE, tracker.wireHandler);
        pipeline.channel().closeFuture().addListener(ignored -> tracker.clear());
        return tracker;
    }

    private void recordLogical(Pending pending, int bytes) {
        packets.increment();
        logicalBytes.add(bytes);
        histogram(logicalHistogram, bytes);
        if (pending.composition == null) {
            uncategorizedPackets.increment();
            protocolBytes.add(bytes);
            return;
        }
        VisualChunkPackets.PacketComposition composition = pending.composition;
        blockStateBytes.add(composition.blockStateBytes());
        biomeBytes.add(composition.biomeBytes());
        heightmapBytes.add(composition.heightmapBytes());
        skyLightBytes.add(composition.skyLightBytes());
        blockLightBytes.add(composition.blockLightBytes());
        lightMaskBytes.add(composition.lightMaskBytes());
        protocolBytes.add(composition.protocolBytes() + Math.max(0, bytes - composition.totalBytes()));
    }

    private void recordCompressed(Pending pending, int bytes) {
        pending.compressedBytes = bytes;
        compressedBytes.add(bytes);
    }

    private void recordWire(Pending pending, int bytes) {
        wireBytes.add(bytes);
    }

    Snapshot drainSnapshot() {
        long[] histogram = new long[HISTOGRAM_BUCKETS];
        long samples = 0;
        for (int index = 0; index < histogram.length; index++) {
            histogram[index] = logicalHistogram[index].getAndSet(0);
            samples += histogram[index];
        }
        return new Snapshot(packets.sumThenReset(), logicalBytes.sumThenReset(), compressedBytes.sumThenReset(),
                wireBytes.sumThenReset(), blockStateBytes.sumThenReset(), biomeBytes.sumThenReset(),
                heightmapBytes.sumThenReset(), skyLightBytes.sumThenReset(), blockLightBytes.sumThenReset(),
                lightMaskBytes.sumThenReset(), protocolBytes.sumThenReset(), uncategorizedPackets.sumThenReset(),
                droppedObservations.sumThenReset(), compressionThreshold.get(), histogram, samples);
    }

    private static AtomicLong[] histogram() {
        AtomicLong[] result = new AtomicLong[HISTOGRAM_BUCKETS];
        for (int index = 0; index < result.length; index++) result[index] = new AtomicLong();
        return result;
    }

    private static void histogram(AtomicLong[] histogram, int bytes) {
        int bucket = Math.min(HISTOGRAM_BUCKETS - 1,
                63 - Long.numberOfLeadingZeros(Math.max(1, bytes)));
        histogram[bucket].incrementAndGet();
    }

    record Snapshot(long packets, long logicalBytes, long compressedBytes, long wireBytes,
            long blockStateBytes, long biomeBytes, long heightmapBytes, long skyLightBytes,
            long blockLightBytes, long lightMaskBytes, long protocolBytes, long uncategorizedPackets,
            long droppedObservations, long compressionThreshold, long[] histogram, long samples) {
        long categorizedBytes() {
            return blockStateBytes + biomeBytes + heightmapBytes + skyLightBytes + blockLightBytes
                    + lightMaskBytes + protocolBytes;
        }
        long averageBytes() { return packets == 0 ? 0 : logicalBytes / packets; }
        long percentileBytes(double percentile) {
            if (samples == 0) return 0;
            long target = Math.max(1L, (long) Math.ceil(samples * percentile));
            long seen = 0;
            for (int index = 0; index < histogram.length; index++) {
                seen += histogram[index];
                if (seen >= target) return 1L << index;
            }
            return 1L << (histogram.length - 1);
        }
    }

    private static final class Tracker extends ChannelOutboundHandlerAdapter {
        private final ViewExtendNetworkMetrics owner;
        private final IdentityHashMap<Packet<?>, ArrayDeque<Pending>> registered = new IdentityHashMap<>();
        private final ArrayDeque<Pending> logicalPending = new ArrayDeque<>();
        private final ArrayDeque<Pending> compressedPending = new ArrayDeque<>();
        private final ArrayDeque<Pending> wirePending = new ArrayDeque<>();
        private final ChannelOutboundHandlerAdapter logicalHandler = new BoundaryHandler(logicalPending, this::onLogical);
        private final ChannelOutboundHandlerAdapter compressedHandler = new BoundaryHandler(compressedPending, this::onCompressed);
        private final ChannelOutboundHandlerAdapter wireHandler = new BoundaryHandler(wirePending, this::onWire);

        private Tracker(ViewExtendNetworkMetrics owner) { this.owner = owner; }

        void register(Packet<?> packet, VisualChunkPackets.PacketComposition composition) {
            registered.computeIfAbsent(packet, ignored -> new ArrayDeque<>()).addLast(new Pending(composition));
        }

        @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
            if (message instanceof Packet<?> packet) {
                ArrayDeque<Pending> pending = registered.get(packet);
                if (pending != null) {
                    Pending next = pending.pollFirst();
                    if (pending.isEmpty()) registered.remove(packet);
                    logicalPending.addLast(next);
                }
            }
            ctx.write(message, promise);
        }

        private void onLogical(Pending pending, int bytes) {
            pending.logicalBytes = bytes;
            owner.recordLogical(pending, bytes);
            compressedPending.addLast(pending);
        }

        private void onCompressed(Pending pending, int bytes) {
            owner.recordCompressed(pending, bytes);
            wirePending.addLast(pending);
        }

        private void onWire(Pending pending, int bytes) { owner.recordWire(pending, bytes); }

        void clear() {
            owner.droppedObservations.add(registered.values().stream().mapToLong(ArrayDeque::size).sum()
                    + logicalPending.size() + compressedPending.size() + wirePending.size());
            registered.clear(); logicalPending.clear(); compressedPending.clear(); wirePending.clear();
        }
    }

    private static final class BoundaryHandler extends ChannelOutboundHandlerAdapter {
        private final ArrayDeque<Pending> pending;
        private final java.util.function.BiConsumer<Pending, Integer> consumer;

        private BoundaryHandler(ArrayDeque<Pending> pending,
                java.util.function.BiConsumer<Pending, Integer> consumer) {
            this.pending = pending;
            this.consumer = consumer;
        }

        @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
            if (message instanceof ByteBuf buffer && !pending.isEmpty()) {
                consumer.accept(pending.pollFirst(), buffer.readableBytes());
            }
            ctx.write(message, promise);
        }
    }

    private static final class Pending {
        private final VisualChunkPackets.PacketComposition composition;
        private int logicalBytes;
        private int compressedBytes;
        private Pending(VisualChunkPackets.PacketComposition composition) { this.composition = composition; }
    }
}
