execute unless data storage full_ghast_ahead:config amount run return 0
execute as @e[type=minecraft:happy_ghast,tag=full_ghast_ahead.on] unless function full_ghast_ahead:driven run function full_ghast_ahead:unboost
execute as @a on vehicle if entity @s[type=minecraft:happy_ghast,tag=!full_ghast_ahead.on] if function full_ghast_ahead:driven run function full_ghast_ahead:boost with storage full_ghast_ahead:config
