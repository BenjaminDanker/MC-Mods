# Once every chapter is done, the story ends. Runs once a second.
execute as @a[advancements={magicstory:awakened=true,magicstory:tower=true,magicstory:temple=true,magicstory:wizard=true,magicstory:fire=true,magicstory:void=true}] unless entity @s[advancements={magicstory:convergence=true}] run advancement grant @s only magicstory:convergence
schedule function magicstory:check 20t
