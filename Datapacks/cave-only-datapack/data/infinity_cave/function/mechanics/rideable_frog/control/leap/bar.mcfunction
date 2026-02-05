execute if score @s ic.frog_leap matches 100.. run scoreboard players set @s ic.frog_leap 100

title @s actionbar ["Charging ",{"score":{"name":"@s","objective":"ic.frog_leap"}},"%"]

scoreboard players add @s ic.frog_leap 1

$playsound entity.frog.eat player @a[distance=..15] ~ ~ ~ 0.1 $(pitch) 1