#!/usr/bin/env python3
"""Writes data/magitech/structure/ley_well.nbt: a small amethyst shrine with a budding-amethyst core.
Run from the mod folder: python3 tools/make_well.py"""
import gzip, math, os, struct

DATA_VERSION = 4903  # Minecraft 26.2

def s(v): b = v.encode(); return struct.pack('>H', len(b)) + b
def tag(t, name, payload): return bytes([t]) + s(name) + payload
def i_(v): return struct.pack('>i', v)
def compound(items): return b''.join(items) + b'\x00'
def tag_int(name, v): return tag(3, name, i_(v))
def tag_str(name, v): return tag(8, name, s(v))
def tag_list_ints(name, vals): return tag(9, name, bytes([3]) + i_(len(vals)) + b''.join(i_(v) for v in vals))
def tag_list_compounds(name, comps): return tag(9, name, bytes([10]) + i_(len(comps)) + b''.join(comps))
def tag_empty_list(name): return tag(9, name, bytes([0]) + i_(0))

SIZE = (9, 7, 9)
CENTER = (4, 4)
blocks = {}  # (x, y, z) -> (name, props)

def put(x, y, z, name, **props): blocks[(x, y, z)] = (name, props)

for x in range(9):
    for z in range(9):
        d = math.hypot(x - CENTER[0], z - CENTER[1])
        if d <= 1.5:
            put(x, 0, z, 'minecraft:amethyst_block')
        elif d <= 3.3:
            put(x, 0, z, 'minecraft:smooth_basalt')
        elif d <= 4.4:
            put(x, 0, z, 'minecraft:calcite')
# the core spire
put(4, 1, 4, 'minecraft:budding_amethyst')
put(4, 2, 4, 'minecraft:amethyst_block')
put(4, 3, 4, 'minecraft:amethyst_block')
put(4, 4, 4, 'minecraft:amethyst_cluster', facing='up', waterlogged='false')
for dx, dz, face in ((1, 0, 'east'), (-1, 0, 'west'), (0, 1, 'south'), (0, -1, 'north')):
    put(4 + dx, 1, 4 + dz, 'minecraft:medium_amethyst_bud', facing=face, waterlogged='false')
    put(4 + 2 * dx, 1, 4 + 2 * dz, 'minecraft:small_amethyst_bud', facing=face, waterlogged='false')
# four corner obelisks
for ox in (1, 7):
    for oz in (1, 7):
        put(ox, 1, oz, 'minecraft:polished_blackstone_bricks')
        put(ox, 2, oz, 'minecraft:chiseled_polished_blackstone')
        put(ox, 3, oz, 'minecraft:polished_blackstone_brick_slab', type='bottom', waterlogged='false')
        put(ox, 4, oz, 'minecraft:amethyst_cluster', facing='up', waterlogged='false')

palette = []
index = {}
for name, props in blocks.values():
    key = (name, tuple(sorted(props.items())))
    if key not in index:
        index[key] = len(palette)
        palette.append(key)
palette_nbt = []
for name, props in palette:
    items = [tag_str('Name', name)]
    if props:
        items.append(tag(10, 'Properties', compound([tag_str(k, v) for k, v in props])))
    palette_nbt.append(compound(items))
block_nbt = []
for (x, y, z), (name, props) in sorted(blocks.items()):
    block_nbt.append(compound([tag_int('state', index[(name, tuple(sorted(props.items())))]), tag_list_ints('pos', [x, y, z])]))
root = compound([
    tag_int('DataVersion', DATA_VERSION),
    tag_list_ints('size', list(SIZE)),
    tag_list_compounds('palette', palette_nbt),
    tag_list_compounds('blocks', block_nbt),
    tag_empty_list('entities'),
])
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/data/magitech/structure')
os.makedirs(out, exist_ok=True)
with gzip.open(os.path.join(out, 'ley_well.nbt'), 'wb') as f:
    f.write(bytes([10]) + s('') + root)
print(len(blocks), 'blocks,', len(palette), 'states')
