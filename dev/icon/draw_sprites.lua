-- Full Ghast Ahead icon sprites. Run through the aseprite MCP: dofile("<abs>/FullGhastAhead/dev/icon/draw_sprites.lua")
-- fx_spark (the lens twinkle) is copied from Veinminer's sprites (same author).
dofile("/home/emppu/Projects/Minecraft Datapacks/.claude/skills/pack-icon-animation/assets/pixel_art.lua")
local OUT = "/home/emppu/Projects/Minecraft Datapacks/FullGhastAhead/dev/icon/sprites/"

local body = { ["0"] = "#55557A", r = "#6E6E8A", s = "#8B8BA6", t = "#ABABC2", u = "#CACAD9", v = "#E2E2EC", w = "#F3F3F8", W = "#FFFFFF" }
local blush = { ["4"] = "#B85B7E", ["3"] = "#E3829F", ["2"] = "#F9A9BF", ["1"] = "#FFD3DE" }
local ink = { z = "#231A33", k = "#3B2F4F", K = "#5C4A73" }
local mouth = { x = "#7E1E36", m = "#B8344E", n = "#F07A8E" }
local leather = { Z = "#24130A", Q = "#3B2111", V = "#5C361A", b = "#82522A", c = "#AE763F", h = "#D9A462" }
local red = { X = "#480B19", R = "#701427", F = "#A92530", E = "#DB3C31", D = "#FF7157" }
local gold = { Y = "#784511", y = "#B26C18", g = "#EAA422", G = "#FFDA4E" }
local lens = { e = "#12343F", o = "#1F5468", p = "#44A2BA", i = "#93E0EC", j = "#F2FFFF" }
local RAMPS = {
  { body, "0rstuvwW" }, { blush, "4321" }, { ink, "zkK" }, { mouth, "xmn" }, { leather, "ZQVbch" },
  { red, "XRFED" }, { gold, "YygG" }, { lens, "eopij" },
}
local function merge(...)
  local out = {}
  for _, t in ipairs({ ... }) do for k, v in pairs(t) do out[k] = v end end
  return out
end
local ALL = merge(body, blush, ink, mouth, leather, red, gold, lens)
-- every colour n steps darker along its own ramp (the render uses shading:false, so faces are pre-shaded).
-- Light comes from the upper right: top = full, south (the face, right on screen) = 1 step, west (left) = 2 steps.
local function darker(n)
  local m = {}
  for _, r in ipairs(RAMPS) do
    local ramp, order = r[1], r[2]
    for i = 1, #order do m[ramp[order:sub(i, i)]] = ramp[order:sub(math.max(1, i - n), math.max(1, i - n))] end
  end
  return m
end
local function shade(name, suffix, n) PA.remap(OUT .. name .. ".aseprite", OUT .. name .. suffix, darker(n)) end

-- harness band shared by all four sides: leather strap, gold trim, red cloth skirt with folds and tabs, cast shadow
local BAND = {
  "cccccccccccccccc",
  "GGGGGGGGGGGGGGGG",
  "EEEFEEEFEEEFEEEF",
  "FFFuFFFuFFFuFFFu",
  "vvvvvvvvvvvvvvvv",
}
local function with_band(rows)
  local out = {}
  for _, r in ipairs(BAND) do out[#out + 1] = r end
  for _, r in ipairs(rows) do out[#out + 1] = r end
  return out
end

-- fluffy white side (west): faint curls, light left edge, dark right and bottom edges
PA.sprite_from_grid(OUT .. "ghast_side", with_band({
  "Wwwwwwwwwwwwwwwu",
  "Wwwwwwwwwwwwwwwu",
  "Wwvvwwwwwwwwwwwu",
  "Wwwwwwwwwwvvwwwu",
  "Wwwwwwwwwwwwwwwu",
  "Wwwwwvvwwwwwwwwu",
  "Wwwwwwwwwwwwvvwu",
  "Wwvvwwwwwwwwwwwu",
  "Wwwwwwwvvwwwwwwu",
  "Wwwwwwwwwwwwwwwu",
  "uuuuuuuuuuuuuuuu",
}), ALL)
shade("ghast_side", "_w", 2)

-- face (south): closed happy eyes, blush, small smile
PA.sprite_from_grid(OUT .. "ghast_face", with_band({
  "Wwwwwwwwwwwwwwwu",
  "Wwwwwwwwwwwwwwwu",
  "Wwwwwwwwwwwwwwwu",
  "Wwwwkkwwwwkkwwwu",
  "Wwwkwwkwwkwwkwwu",
  "Ww222wwwwww222wu",
  "Wwwwwwkwwkwwwwwu",
  "Wwwwwwwkkwwwwwwu",
  "Wwwwwwwwwwwwwwwu",
  "Wwvwwwwwwwwwwvwu",
  "uuuuuuuuuuuuuuuu",
}), ALL)
shade("ghast_face", "_s", 1)

-- top: the harness saddle, leather with a quilted red cushion and gold studs
PA.sprite_from_grid(OUT .. "ghast_top", {
  "hhhhhhhhhhhhhhhc",
  "hcccccccccccccbV",
  "hcGbbbbbbbbbbGbV",
  "hcbbDDDDDDDDbbbV",
  "hcbDEEEEEEEEFbbV",
  "hcbDEEEEEEEEFbbV",
  "hcbDEEEEEEEEFbbV",
  "hcbDFFFFFFFFFbbV",
  "hcbDEEEEEEEEFbbV",
  "hcbDEEEEEEEEFbbV",
  "hcbDEEEEEEEEFbbV",
  "hcbbFFFFFFFFbbbV",
  "hcbbbbbbbbbbbbbV",
  "hcGbbbbbbbbbbGbV",
  "hbVVVVVVVVVVVVVV",
  "cVVVVVVVVVVVVVVQ",
}, ALL)

-- tentacle: 2x8 texels used (UV [0,0,2,len]); lit left column, shadow right under the body
local TENT = { "vu", "Ww", "Ww", "Wv", "Ww", "Wv", "wv", "vu" }
local tent = {}
for y = 1, 16 do tent[y] = (TENT[y] or "..") .. string.rep(".", 14) end
PA.sprite_from_grid(OUT .. "tentacle", tent, ALL)
shade("tentacle", "_s", 1)
shade("tentacle", "_w", 2)

-- goggles front: 12x5 texels used (UV [0,0,12,5]); leather frame, glass lenses with a glint top-left
local GOG = {
  ".cccc..cccc.",
  "bjiipbbjiipb",
  "bippoVVippob",
  "bppoobbppoob",
  ".VVVV..VVVV.",
}
local gog = {}
for y = 1, 16 do gog[y] = (GOG[y] or string.rep(".", 12)) .. "...." end
PA.sprite_from_grid(OUT .. "goggles", gog, ALL)
shade("goggles", "_s", 1)
local edge = {}
for y = 1, 16 do edge[y] = string.rep(y == 1 and "c" or "b", 16) end
PA.sprite_from_grid(OUT .. "goggle_edge", edge, ALL)
shade("goggle_edge", "_w", 2)

-- flat sky background (64x64, x8 = 512px); the clouds are composited per frame by make_icon.py
local function sky_bg(path, w, h, base)
  local px = {}
  for y = 0, h - 1 do for x = 0, w - 1 do px[PA.key(x, y)] = base end end
  PA.save_pixels(path, w, h, px)
end
sky_bg(OUT .. "bg_plain", 64, 64, "#5CB0E8")

-- passing clouds (composited by make_icon.py / make_banner.py on the background's pixel grid, scrolling left):
-- far = small and close to the sky colour, mid = white, front = big and bright, passes in front of the ghast's tentacles
PA.sprite_from_grid(OUT .. "cloud_far", {
  "...ww.....",
  ".wwwwww.w.",
  "wwwwwwwwww",
  ".ssssssss.",
}, { w = "#D3EBFA", s = "#B4D9F2" })
PA.sprite_from_grid(OUT .. "cloud_mid", {
  ".....www........",
  "...wwwwwww.ww...",
  "..wwwwwwwwwwwww.",
  ".wwwwwwwwwwwwwww",
  "wwwwwwwwwwwwwwww",
  ".ssssssssssssss.",
}, { w = "#EEF8FF", s = "#C6E2F6" })
PA.sprite_from_grid(OUT .. "cloud_front", {
  "........wwww............",
  "......wwwwwwww..www.....",
  "...ww.wwwwwwwwwwwwwww...",
  "..wwwwwwwwwwwwwwwwwwwww.",
  ".wwwwwwwwwwwwwwwwwwwwwww",
  "wwwwwwwwwwwwwwwwwwwwwwww",
  "wssswwwwwwwwwwwwwsssswww",
  ".sssssssssssssssssssss..",
}, { w = "#FFFFFF", s = "#D2E7F7" })

-- banner (192x64 grid, x8): flat sky (clouds are composited per frame), two-line title, tagline
sky_bg(OUT .. "banner_bg", 192, 64, "#5CB0E8")
PA.title_sprite(OUT .. "banner_title_1", "FULL GHAST", {
  bands = { "#FFFFFF", "#FFFFFF", "#F2FAFF", "#F2FAFF", "#E0F1FD", "#E0F1FD", "#CDE7FA", "#CDE7FA", "#B7DCF6", "#B7DCF6" },
  extrude = { "#2E4A8A", "#1F3366" },
})
PA.title_sprite(OUT .. "banner_title_2", "AHEAD", {
  bands = { "#FFB199", "#FF7157", "#FF7157", "#FF7157", "#DB3C31", "#DB3C31", "#DB3C31", "#A92530", "#A92530", "#A92530" },
  extrude = { "#701427", "#480B19" },
})
PA.label_sprite(OUT .. "banner_tagline", "HAPPY GHASTS. 2X FASTER.", function(i) return i > 14 and "#FFDA4E" or "#FFFFFF" end)
