#!/usr/bin/env bash
# Dev-only real-client check: ride.sh <mc-version> <speed-percent>
# Runs the real client (headless KWin, sound off) in a copy of the harness world, mounts the player on a harnessed
# happy ghast, holds W and prints the client-side flight speed. Run dev/test/run.sh <ver> first (it creates the world).
set -euo pipefail
VER=${1:?usage: ride.sh <mc-version> <speed-percent>}
SPEED=${2:?speed percent, e.g. 200}
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../../.." && pwd)
CAP=$(cd "$ROOT/../ClientCapture" && pwd)
META=${MODRINTH_META:-$HOME/.local/share/ModrinthApp/meta}
VDIR=$(ls -d "$META"/versions/"$VER"-* | head -1)
VJSON="$VDIR/$(basename "$VDIR").json"
JAVA=$(ls -d "$META"/java_versions/zulu25*/bin | head -1)/java
GAME="$ROOT/dev/test/.work/client-$VER"
CP="$(python3 "$CAP/classpath.py" "$VJSON" "$META/libraries"):$VDIR/$(basename "$VDIR").jar"
# 26.2 ships core LWJGL only as the "unsafe" classifier, which classpath.py skips
for u in $(python3 -c "import json,sys;print(' '.join(l['name'] for l in json.load(open(sys.argv[1]))['libraries'] if l['name'].endswith(':unsafe')))" "$VJSON"); do
  IFS=: read -r g a v c <<< "$u"
  J="$META/libraries/${g//.//}/$a/$v/$a-$v-$c.jar"
  [ -f "$J" ] && CP="$CP:$J"
done

rm -rf "$GAME/modbuild" && mkdir -p "$GAME/modbuild" "$GAME/mods" "$GAME/saves" "$GAME/tmp"
(cd "$HERE/mod/src" && javac -nowarn --release 21 -cp "$CP" -d "$GAME/modbuild" $(find . -name '*.java'))
cp "$HERE/mod/fabric.mod.json" "$HERE/mod/ghastride.mixins.json" "$GAME/modbuild/"
(cd "$GAME/modbuild" && jar --create --file "$GAME/mods/ghastride.jar" .)

rm -rf "$GAME/saves/ride" && cp -r "$ROOT/dev/test/.work/$VER/world" "$GAME/saves/ride"
rm -f "$GAME/saves/ride/session.lock"
cp -r "$HERE/scene" "$GAME/saves/ride/datapacks/ride_scene"
sed -i "s/SPEED/$SPEED/" "$GAME/saves/ride/datapacks/ride_scene/data/ride_scene/function/mount.mcfunction"
cat > "$GAME/options.txt" <<'OPT'
onboardAccessibility:false
soundCategory_master:0.0
pauseOnLostFocus:false
renderDistance:6
simulationDistance:5
maxFps:60
guiScale:2
narrator:0
tutorialStep:none
skipMultiplayerWarning:true
joinedFirstServer:true
OPT
ASSET_INDEX=$(python3 -c "import json,sys;print(json.load(open(sys.argv[1]))['assetIndex']['id'])" "$VJSON")
cat > "$GAME/launch.sh" <<LAUNCH
#!/usr/bin/env bash
cd "$GAME"
exec "$JAVA" -Xmx3G -XX:StackShadowPages=32 --enable-native-access=ALL-UNNAMED --add-exports java.base/jdk.internal.misc=ALL-UNNAMED \\
  -Dorg.lwjgl.system.SharedLibraryExtractPath="$GAME/natives" -Djava.io.tmpdir="$GAME/tmp" \\
  "-DFabricMcEmu= net.minecraft.client.main.Main " \\
  -cp "$CP" net.fabricmc.loader.impl.launch.knot.KnotClient \\
  --username GhastRider --uuid 5e1dcaa0-0000-4000-8000-000000000002 --accessToken 0 --version $VER --versionType release \\
  --gameDir "$GAME" --assetsDir "$META/assets" --assetIndex $ASSET_INDEX \\
  --width 1280 --height 720 --quickPlaySingleplayer ride > "$GAME/client.log" 2>&1
LAUNCH
chmod +x "$GAME/launch.sh"
STUB="${XDG_RUNTIME_DIR:-/tmp}/ghastride-$$.sh"
printf '#!/usr/bin/env bash\nexec "%s"\n' "$GAME/launch.sh" > "$STUB"
chmod +x "$STUB"
set +e
env -u WAYLAND_DISPLAY -u DISPLAY timeout 240 dbus-run-session -- \
  kwin_wayland --virtual --xwayland --no-lockscreen --width 1280 --height 720 --socket ghastride-$$ \
  --exit-with-session "$STUB" > "$GAME/kwin.log" 2>&1
STATUS=$?
set -e
rm -f "$STUB"
echo "exit $STATUS (speed setting $SPEED%)"
grep -E '\[ghastride\]|moved too quickly|moved wrongly|FATAL|Exception in' "$GAME/client.log" | cut -c1-400 | head -20 || true
