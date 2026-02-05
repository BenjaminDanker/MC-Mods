data modify storage ic:cooldown item set from entity @s Item

tag @s add ic.cooldown

execute on origin if items entity @s weapon.mainhand snowball[custom_data~{infinity_cave:{item:"nimbus_nunchucks"}}] run advancement revoke @s only infinity_cave:technical/nimbus_nunchucks/mainhand
execute on origin unless items entity @s weapon.mainhand snowball[custom_data~{infinity_cave:{item:"nimbus_nunchucks"}}] if items entity @s weapon.offhand snowball[custom_data~{infinity_cave:{item:"nimbus_nunchucks"}}] run advancement revoke @s only infinity_cave:technical/nimbus_nunchucks/offhand