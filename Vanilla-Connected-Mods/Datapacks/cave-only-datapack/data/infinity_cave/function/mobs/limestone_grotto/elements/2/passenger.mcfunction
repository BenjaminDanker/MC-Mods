item modify entity @s weapon.mainhand [{function:"set_enchantments",enchantments:{"infinity_cave:technical/elements/limestone_grotto/2":1}}]

scoreboard players set @s ic.tagged 1

execute on vehicle run item modify entity @s armor.feet {function:"set_enchantments",enchantments:{"infinity_cave:technical/elements/limestone_grotto/2":0}}