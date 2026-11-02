$execute on origin run item modify entity @s weapon.offhand [{"function":"minecraft:set_item","item":"$(id)"},{"function":"minecraft:set_components","components":$(components)}]

stopsound @a[distance=..15] * entity.snowball.throw

kill @s