execute as @e[type=minecraft:happy_ghast,tag=full_ghast_ahead.on] run function full_ghast_ahead:unboost
data remove storage full_ghast_ahead:config speed
data remove storage full_ghast_ahead:config amount
data remove storage full_ghast_ahead:config label
data remove storage full_ghast_ahead:config bps
data remove storage full_ghast_ahead:presets list
data remove storage full_ghast_ahead:menu row
data remove storage full_ghast_ahead:meta version
tellraw @s ["",{text:"Full Ghast Ahead removed. ",color:"gold"},{text:"Now delete or disable the datapack (e.g. /datapack disable \"file/FullGhastAhead-1.0.0.zip\") so it does not reinstall on the next /reload.",color:"gray"}]
