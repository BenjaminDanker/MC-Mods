summon item_display ~ ~ ~ {UUID:[I;0,0,0,1],view_range:0f,width:0f,height:0f,item:{id:"minecraft:player_head",count:1}}
item modify entity 0-0-0-0-1 container.0 {function:fill_player_head,entity:this}
data modify storage infinity_cave:data player.name set from entity 0-0-0-0-1 item.components.minecraft:profile.name
kill 0-0-0-0-1