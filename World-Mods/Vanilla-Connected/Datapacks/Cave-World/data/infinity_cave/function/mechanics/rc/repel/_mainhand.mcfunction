advancement revoke @s only infinity_cave:mechanics/rc/repel/_mainhand

data modify storage ic:items repel set from entity @s SelectedItem.components."minecraft:custom_data".infinity_cave

scoreboard players set @s ic.rc_cd 0

function infinity_cave:mechanics/rc/repel/apply with storage ic:items repel

tellraw @s ["",{"text":"[","color":"dark_gray"},{"text":"Infinity Cave","color":"#4C7ED2"},{"text":"]","color":"dark_gray"},{"text":" Used Repel!","color":"gray"}]

playsound entity.cat.hiss player @s ~ ~ ~ 1 2 1