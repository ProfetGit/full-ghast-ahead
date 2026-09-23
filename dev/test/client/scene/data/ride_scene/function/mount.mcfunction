kill @e[type=!minecraft:player]
function full_ghast_ahead:pick {speed:SPEED}
tag @s add rs.me
execute positioned ~ ~60 ~ summon minecraft:happy_ghast run function ride_scene:setup
tag @s remove rs.me
rotate @s 0 0
tag @s add rs.done
