scoreboard players set .iterate ic.int 0

$bossbar add infinity_cave:$(id) {"entity":"@s","interpret":true,"nbt":"CustomName"}

$bossbar set infinity_cave:$(id) max $(health)
$bossbar set infinity_cave:$(id) value $(health)
$bossbar set infinity_cave:$(id) style $(style)
$bossbar set infinity_cave:$(id) color $(color)