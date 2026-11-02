$execute positioned ~ ~2 ~ run spreadplayers ~ ~ 10 10 under $(int) false @s

effect give @s instant_health 1 1 true

playsound entity.player.teleport hostile @a[distance=..25] ~ ~ ~ 1 1 1
particle portal ~ ~ ~ 0.25 0.5 0.25 0.3 10 normal