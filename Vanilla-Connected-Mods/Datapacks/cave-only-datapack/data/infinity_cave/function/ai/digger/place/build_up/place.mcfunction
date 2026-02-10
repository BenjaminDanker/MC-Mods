effect give @s slowness 1 255 true

setblock ~ ~ ~ cobbled_deepslate
playsound block.deepslate.place hostile @a[distance=..15]
particle block{block_state:cobbled_deepslate} ~ ~ ~ 0.25 0.25 0.25 0.1 5 normal

scoreboard players set @s ic.ai 1