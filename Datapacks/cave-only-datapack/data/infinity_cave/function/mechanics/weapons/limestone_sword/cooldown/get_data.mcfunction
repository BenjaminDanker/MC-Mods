data modify storage ic:cooldown item set from entity @s Item

tag @s add ic.cooldown

execute on origin if items entity @s weapon.mainhand snowball[custom_data~{infinity_cave:{item:"limestone_sword"}}] run advancement revoke @s only infinity_cave:technical/limestone_sword/mainhand
execute on origin unless items entity @s weapon.mainhand snowball[custom_data~{infinity_cave:{item:"limestone_sword"}}] if items entity @s weapon.offhand snowball[custom_data~{infinity_cave:{item:"limestone_sword"}}] run advancement revoke @s only infinity_cave:technical/limestone_sword/offhand