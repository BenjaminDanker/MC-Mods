item modify entity @s weapon.mainhand [{function:"set_enchantments",enchantments:{"infinity_cave:technical/elements/amethyst_caves/1":1}}]

ride @n[type=end_crystal] mount @s

scoreboard players set @s ic.tagged 1

execute on vehicle run item modify entity @s armor.feet {function:"set_enchantments",enchantments:{"infinity_cave:technical/elements/amethyst_caves/1":0}}