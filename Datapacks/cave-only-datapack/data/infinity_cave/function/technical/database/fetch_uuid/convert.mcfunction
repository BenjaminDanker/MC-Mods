data merge storage infinity_cave:temp {0:0,1:0,2:0,3:0,4:0,5:0,6:0,7:0,8:0,9:0,a:0,b:0,c:0,d:0,e:0,f:0}
$data modify storage infinity_cave:main in set value $(UUID)

execute store result score 0= ic.main store result score 1= ic.main run data get storage infinity_cave:main in[0]
execute store result storage infinity_cave:temp 0 int 1 run scoreboard players operation 0= ic.main %= 256 num
execute store result score 2= ic.main run scoreboard players operation 1= ic.main /= 256 num
execute store result storage infinity_cave:temp 1 int 1 run scoreboard players operation 1= ic.main %= 256 num
execute store result score 3= ic.main run scoreboard players operation 2= ic.main /= 256 num
execute store result storage infinity_cave:temp 2 int 1 run scoreboard players operation 2= ic.main %= 256 num
execute store result storage infinity_cave:temp 3 int 1 run scoreboard players operation 3= ic.main /= 256 num

execute store result score 0= ic.main store result score 1= ic.main run data get storage infinity_cave:main in[1]
execute store result storage infinity_cave:temp 4 int 1 run scoreboard players operation 0= ic.main %= 256 num
execute store result score 2= ic.main run scoreboard players operation 1= ic.main /= 256 num
execute store result storage infinity_cave:temp 5 int 1 run scoreboard players operation 1= ic.main %= 256 num
execute store result score 3= ic.main run scoreboard players operation 2= ic.main /= 256 num
execute store result storage infinity_cave:temp 6 int 1 run scoreboard players operation 2= ic.main %= 256 num
execute store result storage infinity_cave:temp 7 int 1 run scoreboard players operation 3= ic.main /= 256 num

execute store result score 0= ic.main store result score 1= ic.main run data get storage infinity_cave:main in[2]
execute store result storage infinity_cave:temp 8 int 1 run scoreboard players operation 0= ic.main %= 256 num
execute store result score 2= ic.main run scoreboard players operation 1= ic.main /= 256 num
execute store result storage infinity_cave:temp 9 int 1 run scoreboard players operation 1= ic.main %= 256 num
execute store result score 3= ic.main run scoreboard players operation 2= ic.main /= 256 num
execute store result storage infinity_cave:temp a int 1 run scoreboard players operation 2= ic.main %= 256 num
execute store result storage infinity_cave:temp b int 1 run scoreboard players operation 3= ic.main /= 256 num

execute store result score 0= ic.main store result score 1= ic.main run data get storage infinity_cave:main in[3]
execute store result storage infinity_cave:temp c int 1 run scoreboard players operation 0= ic.main %= 256 num
execute store result score 2= ic.main run scoreboard players operation 1= ic.main /= 256 num
execute store result storage infinity_cave:temp d int 1 run scoreboard players operation 1= ic.main %= 256 num
execute store result score 3= ic.main run scoreboard players operation 2= ic.main /= 256 num
execute store result storage infinity_cave:temp e int 1 run scoreboard players operation 2= ic.main %= 256 num
execute store result storage infinity_cave:temp f int 1 run scoreboard players operation 3= ic.main /= 256 num

function infinity_cave:technical/database/fetch_uuid/zzz/get_hexes with storage infinity_cave:temp
function infinity_cave:technical/database/fetch_uuid/zzz/concat_uuid with storage infinity_cave:temp