package com.silver.arcade.realm;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks written straight into chunk data never get the shape update a placed block gets from its neighbours (fences,
 * walls, panes and bars connecting, stairs forming corners, ...). This settles a room's blocks the way placing them
 * would have, off the server thread: every block whose class reacts to neighbour shapes is run through its own
 * {@code updateShape} for the six neighbours, against the room's block map as the world.
 */
final class Shapes {
    private static final Map<Class<?>, Boolean> REACTS = new ConcurrentHashMap<>();
    private static final Direction[] ORDER = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.DOWN, Direction.UP};

    private static final java.util.Set<Class<?>> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private Shapes() {
    }

    static boolean reacts(Block block) {
        return REACTS.computeIfAbsent(block.getClass(), type -> {
            for (Class<?> c = type; c != null && c != BlockBehaviour.class && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals("updateShape") && m.getParameterCount() == 8) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    /** What the world looks like to a block asking its neighbours. */
    private static final class View implements InvocationHandler {
        private final Long2ObjectMap<BlockState> map;
        private final BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        private final RandomSource random = RandomSource.create(1L);
        private final Object noTicks = Proxy.newProxyInstance(Shapes.class.getClassLoader(), new Class<?>[] {net.minecraft.world.ticks.LevelTickAccess.class},
            (proxy, method, args) -> {
                Class<?> type = method.getReturnType();
                return type == boolean.class ? (Object) false : type == int.class ? (Object) 0 : null;
            });

        View(Long2ObjectMap<BlockState> map) {
            this.map = map;
        }

        BlockState at(BlockPos pos) {
            BlockState state = map.get(pos.asLong());
            return state == null ? air : state;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "getBlockState":
                    return at((BlockPos) args[0]);
                case "getFluidState":
                    return at((BlockPos) args[0]).getFluidState();
                case "getBlockEntity":
                    return null;
                case "isClientSide":
                    return false;
                case "getMinY":
                    return -64;
                case "getMaxY":
                    return 319;
                case "getHeight":
                    return 384;
                case "getRandom":
                    return random;
                case "getBlockTicks":
                case "getFluidTicks":
                    return noTicks; // nothing is scheduled in a room that is only being written
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "ArenaView";
                default:
                    break;
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            Class<?> type = method.getReturnType();
            if (type == boolean.class) {
                return false;
            } else if (type == int.class) {
                return 0;
            } else if (type == long.class) {
                return 0L;
            } else if (type == double.class) {
                return 0D;
            } else if (type == float.class) {
                return 0F;
            }
            return null;
        }
    }

    /**
     * Settles the states in {@code map} in place until nothing changes any more (a block's shape can depend on what its
     * neighbour's shape became: a wall under a wall, a chain of pointed dripstone). Returns {blocks that react, changes}.
     */
    static int[] settle(Long2ObjectMap<BlockState> map) {
        View view = new View(map);
        Object proxy = Proxy.newProxyInstance(Shapes.class.getClassLoader(), new Class<?>[] {LevelReader.class, ScheduledTickAccess.class}, view);
        LevelReader level = (LevelReader) proxy;
        ScheduledTickAccess ticks = (ScheduledTickAccess) proxy;
        RandomSource random = RandomSource.create(1L);
        int changed = 0;
        int reacting = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        LongArrayList queue = new LongArrayList();
        LongOpenHashSet queued = new LongOpenHashSet();
        for (Long2ObjectMap.Entry<BlockState> entry : map.long2ObjectEntrySet()) {
            BlockState state = entry.getValue();
            Block block = state.getBlock();
            if (block instanceof LeavesBlock && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT)) {
                // no tick will correct the distance of a block written into chunk data: keep the leaves from decaying
                entry.setValue(state.setValue(LeavesBlock.PERSISTENT, true));
                changed++;
            } else if (reacts(block)) {
                reacting++;
                queue.add(entry.getLongKey());
                queued.add(entry.getLongKey());
            }
        }
        long budget = Math.max(1000L, 40L * queue.size());
        for (int head = 0; head < queue.size() && budget-- > 0; head++) {
            long key = queue.getLong(head);
            queued.remove(key);
            BlockState state = map.get(key);
            if (state == null) {
                continue;
            }
            pos.set(key);
            BlockState current = state;
            try {
                for (Direction direction : ORDER) {
                    neighbour.setWithOffset(pos, direction);
                    BlockState other = view.at(neighbour);
                    BlockState next = current.updateShape(level, ticks, pos, direction, neighbour, other, random);
                    if (next.getBlock() == current.getBlock()) {
                        current = next; // (a block that would break instead is left as the room placed it)
                    }
                }
            } catch (Throwable failure) {
                if (WARNED.add(state.getBlock().getClass())) {
                    org.slf4j.LoggerFactory.getLogger("arcade").warn("Shape settle failed for {} (once per block class)", state, failure);
                }
                continue;
            }
            if (current != state) {
                map.put(key, current);
                changed++;
                // whatever sits next to it may now settle differently
                for (Direction direction : Direction.values()) {
                    neighbour.setWithOffset(pos, direction);
                    long other = neighbour.asLong();
                    BlockState near = map.get(other);
                    if (near != null && reacts(near.getBlock()) && queued.add(other)) {
                        queue.add(other);
                    }
                }
            }
        }
        return new int[] {reacting, changed};
    }
}
