# hit detection
function infinity_cave:technical/hit_match/player_hurt_entity/setup/setup_id

execute if entity @s[tag=legendary] run function infinity_cave:mobs/assign_legendary with storage infinity_cave:mob data

$item modify entity @s[tag=$(tier)] armor.head infinity_cave:enchants/mob/$(enchant)
$item modify entity @s[tag=$(tier)] armor.chest infinity_cave:enchants/mob/$(enchant)
$item modify entity @s[tag=$(tier)] armor.legs infinity_cave:enchants/mob/$(enchant)
$item modify entity @s[tag=$(tier)] armor.feet infinity_cave:enchants/mob/$(enchant)
$item modify entity @s[tag=$(tier)] weapon infinity_cave:enchants/mob/$(enchant)