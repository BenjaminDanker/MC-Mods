execute if entity @s[predicate=!infinity_cave:has_curse] run function infinity_cave:mechanics/rc/curse_remover/no_curse

execute if entity @s[level=..14,predicate=infinity_cave:has_curse] run function infinity_cave:mechanics/rc/curse_remover/not_enough

execute if entity @s[level=15..,predicate=infinity_cave:has_curse] run function infinity_cave:mechanics/rc/curse_remover/remove_curse

scoreboard players set @s ic.rc_cd 0

advancement revoke @s only infinity_cave:mechanics/rc/curse_remover