item modify entity @s weapon.mainhand [{function:"set_enchantments",enchantments:{"infinity_cave:technical/elements/sulfide_caves/1":1}}]

scoreboard players set @s ic.tagged 1

execute on vehicle run item modify entity @s armor.feet {function:"set_enchantments",enchantments:{"infinity_cave:technical/elements/sulfide_caves/1":0}}
