$item modify entity @s weapon.offhand [{function:"minecraft:set_components", \
components:{"minecraft:item_model":"infinity_cave:amethyst_charge","minecraft:custom_data":{infinity_cave:{"item":"amethyst_charge","tier": 3,"cooldown": 3,amethyst_charge:{charged:true,xp_amount:$(amount)}}}}},\
{function:"minecraft:set_lore",entity:"this",lore:[{text:"⬤ $(levels) levels",color:"green",italic:0}],mode:"replace_all"},\
{function:"minecraft:set_components",components:{"!minecraft:use_remainder":{}}}]