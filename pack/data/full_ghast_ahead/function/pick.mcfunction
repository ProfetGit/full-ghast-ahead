$data modify storage full_ghast_ahead:config {} merge from storage full_ghast_ahead:presets list[{speed:$(speed)}]
execute as @e[type=minecraft:happy_ghast,tag=full_ghast_ahead.on] run function full_ghast_ahead:boost with storage full_ghast_ahead:config
