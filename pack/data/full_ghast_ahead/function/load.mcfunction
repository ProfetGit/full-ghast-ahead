data modify storage full_ghast_ahead:meta version set value "1.0.0"
data modify storage full_ghast_ahead:presets list set value [{speed:100,amount:0.0d,label:"1×",bps:"3.6"},{speed:150,amount:0.2247448714d,label:"1.5×",bps:"5.4"},{speed:200,amount:0.4142135624d,label:"2×",bps:"7.2"},{speed:250,amount:0.5811388301d,label:"2.5×",bps:"9.0"},{speed:300,amount:0.7320508076d,label:"3×",bps:"10.8"},{speed:400,amount:1.0d,label:"4×",bps:"14.4"}]
execute unless data storage full_ghast_ahead:config speed run data modify storage full_ghast_ahead:config speed set value 200
function full_ghast_ahead:pick with storage full_ghast_ahead:config
