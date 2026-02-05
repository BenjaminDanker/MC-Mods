data modify storage ic:rapid_breeze motion set from entity @s Motion

kill @s

particle gust_emitter_small ~ ~ ~

playsound entity.breeze.wind_burst hostile @a[distance=..30] ~ ~ ~ 0.2 2 1

function infinity_cave:ai/rapid_breeze/wind_charge/wind_charge with storage ic:rapid_breeze