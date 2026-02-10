$data modify storage infinity_cave:geraldo current set from storage infinity_cave:geraldo nbt[$(int)]
$data modify storage infinity_cave:geraldo current.gadget set from storage infinity_cave:geraldo gadgets[$(int)]

function infinity_cave:ai/geraldo/gadgets/spawn with storage infinity_cave:geraldo current

scoreboard players add #tmp ic.const 1

$execute if score #tmp ic.const matches ..$(amount) run return run function infinity_cave:ai/geraldo/gadgets/pool with storage infinity_cave:geraldo