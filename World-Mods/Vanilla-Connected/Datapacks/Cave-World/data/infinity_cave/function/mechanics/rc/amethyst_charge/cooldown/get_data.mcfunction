data modify storage ic:cooldown item set from entity @s Item

tag @s add ic.cooldown

execute on origin if items entity @s weapon.mainhand snowball[custom_data~{infinity_cave:{item:"amethyst_charge"}}] run schedule function infinity_cave:mechanics/rc/amethyst_charge/cooldown/mainhand/schedule 1t
execute on origin unless items entity @s weapon.mainhand snowball[custom_data~{infinity_cave:{item:"amethyst_charge"}}] if items entity @s weapon.offhand snowball[custom_data~{infinity_cave:{item:"amethyst_charge"}}] run schedule function infinity_cave:mechanics/rc/amethyst_charge/cooldown/offhand/schedule 1t