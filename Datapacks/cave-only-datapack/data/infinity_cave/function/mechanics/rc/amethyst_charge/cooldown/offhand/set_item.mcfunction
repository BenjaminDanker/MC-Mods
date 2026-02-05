kill @s

stopsound @a[distance=..15] * entity.snowball.throw

execute if data storage ic:cooldown item.components."minecraft:custom_data".infinity_cave.amethyst_charge.xp_amount on origin at @s run return run function infinity_cave:mechanics/rc/amethyst_charge/exp/get

$execute on origin run item modify entity @s weapon.offhand [{"function":"minecraft:set_item","item":"$(id)"},{"function":"minecraft:set_components","components":$(components)}]

execute on origin run function infinity_cave:mechanics/rc/amethyst_charge/exp/offhand/get