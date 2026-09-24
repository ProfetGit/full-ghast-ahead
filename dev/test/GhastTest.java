import java.lang.reflect.Method;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.minecraft.network.syncher.EntityDataAccessor;

public class GhastTest {
    static MinecraftServer server;
    static ServerLevel level;
    static ServerPlayer player;
    static EmbeddedChannel channel;
    static int passed, failed;
    static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        server = boot();
        long deadline = System.currentTimeMillis() + 180_000;
        while (!server.isReady()) {
            if (System.currentTimeMillis() > deadline) throw new IllegalStateException("server never became ready");
            if (server.isStopped()) { System.out.println("[FAIL] server failed to start"); System.exit(1); }
            Thread.sleep(50);
        }
        ticks(20);
        level = server.overworld();
        try {
            if (args.length >= 2 && args[0].equals("explore")) {
                explore(Path.of(args[1]));
            } else {
                Scenarios.run();
                System.out.println("SUMMARY " + passed + " passed, " + failed + " failed");
                for (String f : failures) System.out.println("  - " + f);
            }
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            failed++;
        } finally {
            on(() -> { server.halt(false); return null; });
            Thread.sleep(3000);
            System.exit(failed == 0 ? 0 : 1);
        }
    }

    /** Vanilla by default; -Dharness.main=<class> boots a plugin platform in-process instead (PLATFORM= in run.sh). */
    static MinecraftServer boot() throws Exception {
        String main = System.getProperty("harness.main");
        if (main == null) {
            net.minecraft.server.Main.main(new String[] {"--nogui"});
            return findServer();
        }
        Class.forName(main).getMethod("main", String[].class).invoke(null, (Object) new String[] {"--nogui"});
        java.lang.reflect.Method get = MinecraftServer.class.getMethod("getServer");
        long deadline = System.currentTimeMillis() + 180_000;
        Object s;
        while ((s = get.invoke(null)) == null) {
            if (System.currentTimeMillis() > deadline) throw new IllegalStateException("server never started");
            Thread.sleep(50);
        }
        return (MinecraftServer) s;
    }

    @SuppressWarnings("unchecked")
    static MinecraftServer findServer() throws Exception {
        Class<?> hooksClass = Class.forName("java.lang.ApplicationShutdownHooks");
        Field hooksField = hooksClass.getDeclaredField("hooks");
        hooksField.setAccessible(true);
        Map<Thread, Thread> hooks = (Map<Thread, Thread>) hooksField.get(null);
        for (Thread t : hooks.keySet()) {
            for (Field f : t.getClass().getDeclaredFields()) {
                if (MinecraftServer.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return (MinecraftServer) f.get(t);
                }
            }
        }
        throw new IllegalStateException("server instance not found in shutdown hooks");
    }

    static <T> T on(Callable<T> task) {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        server.submit(() -> {
            try {
                out.set(task.call());
            } catch (Throwable t) {
                err.set(t);
            }
        }).join();
        if (err.get() != null) throw new RuntimeException(err.get());
        return out.get();
    }

    static void ticks(int n) throws InterruptedException {
        int target = server.getTickCount() + n;
        while (server.getTickCount() < target) Thread.sleep(2);
    }

    /** Runs a console command on the server thread and returns its chat output. */
    static List<String> cmd(String command) {
        return on(() -> {
            List<String> out = new ArrayList<>();
            // a proxy, not an anonymous class: plugin platforms add methods (getBukkitSender), answered by the server
            CommandSource capture = (CommandSource) java.lang.reflect.Proxy.newProxyInstance(CommandSource.class.getClassLoader(),
                new Class<?>[] {CommandSource.class}, (proxy, m, a) -> switch (m.getName()) {
                    case "sendSystemMessage" -> { out.add(((Component) a[0]).getString()); yield null; }
                    case "acceptsSuccess", "acceptsFailure" -> true;
                    case "shouldInformAdmins" -> false;
                    default -> m.invoke(server, a);
                });
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture), command);
            return out;
        });
    }

    static void explore(Path file) throws Exception {
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank() || line.startsWith("//")) continue;
            if (line.startsWith("!tick ")) { ticks(Integer.parseInt(line.substring(6).trim())); continue; }
            if (line.equals("!player")) { spawnPlayer(); continue; }
            if (line.startsWith("!sneak ")) { sneak(Boolean.parseBoolean(line.substring(7).trim())); continue; }
            if (line.startsWith("!find ")) {
                String[] p = line.substring(6).trim().split(" ");
                int[] v = new int[6];
                for (int i = 0; i < 6; i++) v[i] = Integer.parseInt(p[i + 1]);
                String want = p[0];
                System.out.println("[EXPLORE] find " + want + ": " + on(() -> {
                    StringBuilder sb = new StringBuilder();
                    int n = 0;
                    for (BlockPos q : BlockPos.betweenClosed(v[0], v[1], v[2], v[3], v[4], v[5])) {
                        String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(q).getBlock()).toString();
                        if (id.contains(want)) { n++; if (n <= 60) sb.append(q.toShortString()).append(" ").append(level.getBlockState(q).toString().replace("Block{minecraft:", "").replace("}", "")).append("; "); }
                    }
                    return n + " -> " + sb;
                }));
                continue;
            }
            if (line.startsWith("!time ")) {
                String c = line.substring(6).trim();
                long t0 = System.nanoTime();
                List<String> out = cmd(c);
                System.out.println("[EXPLORE] " + String.format("%7.2f ms", (System.nanoTime() - t0) / 1e6) + "  " + c + (out.isEmpty() ? "" : "  -> " + out));
                continue;
            }
            if (line.startsWith("!timed ")) {
                long[] before = tickTimes();
                ticks(Integer.parseInt(line.substring(7).trim()));
                System.out.println("[EXPLORE] worst tick " + worstTickSince(before) / 100_000 / 10.0 + " ms");
                continue;
            }
            if (line.startsWith("!place ")) {
                String[] p = line.substring(7).trim().split(" ");
                BlockPos pos = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                System.out.println("[EXPLORE] place on " + pos + " " + p[3] + " -> " + place(pos, Direction.byName(p[3])));
                continue;
            }
            if (line.startsWith("!destroy ")) {
                String[] p = line.substring(9).trim().split(" ");
                BlockPos pos = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                System.out.println("[EXPLORE] destroy " + pos + " -> " + destroy(pos) + " items " + itemsNear(pos, 3));
                continue;
            }
            List<String> out = cmd(line);
            System.out.println("[EXPLORE] > " + line);
            for (String o : out) System.out.println("[EXPLORE]     " + o.replace("\n", "\n[EXPLORE]     "));
        }
    }

    static void spawnPlayer() {
        if (player != null) return;
        on(() -> {
            GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes("GhastTester".getBytes()), "GhastTester");
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
            ServerPlayer p = new ServerPlayer(server, level, profile, cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(new ChannelHandler[] {connection});
            server.getPlayerList().placeNewPlayer(connection, p, cookie);
            player = p;
            return null;
        });
    }

    /** Drains chat/action-bar packets sent to the mock player: "text {clicks=n}". */
    static List<String> chat() {
        return on(() -> {
            List<String> out = new ArrayList<>();
            Object o;
            while ((o = channel.readOutbound()) != null) {
                if (o instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket p)
                    out.add((p.overlay() ? "[actionbar] " : "") + p.content().getString().replace("\n", "⏎") + " {clicks=" + clicks(p.content()) + "}");
                else if (o instanceof net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket p)
                    out.add("[actionbar] " + p.text().getString());
            }
            return out;
        });
    }

    static int clicks(Component c) {
        int n = c.getStyle().getClickEvent() != null ? 1 : 0;
        for (Component s : c.getSiblings()) n += clicks(s);
        return n;
    }

    static void sneak(boolean on) {
        on(() -> {
            player.setShiftKeyDown(on);
            player.setPose(on ? Pose.CROUCHING : Pose.STANDING);
            return null;
        });
    }

    static boolean destroy(BlockPos pos) {
        return on(() -> player.gameMode.destroyBlock(pos));
    }

    static String blockId(BlockPos pos) {
        return on(() -> {
            BlockState s = level.getBlockState(pos);
            return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        });
    }

    static Map<String, Integer> itemsNear(BlockPos pos, double r) {
        return on(() -> {
            Map<String, Integer> m = new java.util.TreeMap<>();
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(r))) {
                ItemStack s = e.getItem();
                m.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
            }
            return m;
        });
    }

    static int xpNear(BlockPos pos, double r) {
        return on(() -> {
            int total = 0;
            for (ExperienceOrb o : level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(r))) total += o.getValue();
            return total;
        });
    }

    /** Places the held item against a block face through the vanilla use-item path, looking at the face first. */
    static String place(BlockPos against, Direction face) {
        return on(() -> {
            Vec3 hit = Vec3.atCenterOf(against).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            lookAt(hit);
            BlockHitResult r = new BlockHitResult(hit, face, against, false);
            return player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, r).toString();
        });
    }

    static void lookAt(Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
    }

    @SuppressWarnings("unchecked")
    static long worstTickSince(long[] before) {
        long[] after = on(() -> server.getTickTimesNanos().clone());
        long w = 0;
        for (int i = 0; i < after.length; i++) if (after[i] != before[i]) w = Math.max(w, after[i]);
        return w;
    }

    static long[] tickTimes() {
        return on(() -> server.getTickTimesNanos().clone());
    }

    static ItemStack mainhand() {
        return on(() -> player.getMainHandItem().copy());
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("[PASS] " + name + (detail.isEmpty() ? "" : "  (" + detail + ")"));
        } else {
            failed++;
            failures.add(name + ": " + detail);
            System.out.println("[FAIL] " + name + "  (" + detail + ")");
        }
    }

    static void info(String msg) {
        System.out.println("[INFO] " + msg);
    }
}

class Scenarios {
    static List<String> cmd(String c) { return GhastTest.cmd(c); }
    static void tick(int n) throws InterruptedException { GhastTest.ticks(n); }
    static void check(String n, boolean ok, String d) { GhastTest.check(n, ok, d); }
    static void info(String m) { GhastTest.info(m); }
    static <T> T on(Callable<T> t) { return GhastTest.on(t); }

    static String get(String what) {
        List<String> out = cmd("data get " + what);
        if (out.isEmpty()) return "";
        String o = out.get(0);
        int k = o.indexOf(": ");
        return k < 0 ? "" : o.substring(k + 2).replace("\"", "").trim();
    }
    static LivingEntity find(String tag) {
        for (Entity e : GhastTest.level.getAllEntities()) if (e.entityTags().contains(tag)) return (LivingEntity) e;
        throw new IllegalStateException("no entity tagged " + tag);
    }
    static double fs(String tag) { return on(() -> find(tag).getAttributeValue(Attributes.FLYING_SPEED)); }
    static boolean boosted(String tag) { return on(() -> find(tag).entityTags().contains("full_ghast_ahead.on")); }
    static boolean near(double a, double b, double eps) { return Math.abs(a - b) <= eps; }
    static String f(double v) { return String.format("%.5f", v); }

    /**
     * Replays the client-side ridden flight (LivingEntity.travelRidden: getRiddenInput -> tickRidden -> travel) for n
     * ticks with the given input and returns the mean per-tick displacement over the last 20 ticks.
     */
    static Vec3 fly(String tag, float forward, boolean up, int n) {
        return on(() -> {
            HappyGhast g = (HappyGhast) find(tag);
            ServerPlayer p = GhastTest.player;
            Method input = HappyGhast.class.getDeclaredMethod("getRiddenInput", Player.class, Vec3.class);
            Method ridden = HappyGhast.class.getDeclaredMethod("tickRidden", Player.class, Vec3.class);
            input.setAccessible(true);
            ridden.setAccessible(true);
            p.zza = forward;
            p.xxa = 0;
            p.setJumping(up);
            p.setXRot(0);
            p.setYRot(0);
            g.setYRot(0);
            g.setDeltaMovement(Vec3.ZERO);
            Vec3 start = null;
            for (int i = 0; i < n; i++) {
                if (i == n - 20) start = g.position();
                Vec3 in = (Vec3) input.invoke(g, p, Vec3.ZERO);
                ridden.invoke(g, p, in);
                g.travel(in);
            }
            Vec3 d = g.position().subtract(start).scale(1 / 20.0);
            p.zza = 0;
            p.setJumping(false);
            g.setDeltaMovement(Vec3.ZERO);
            return d;
        });
    }

    static void reset(String tag) {
        cmd("tp @e[tag=" + tag + "] 0.5 -20 0.5");
    }

    static void run() throws Exception {
        cmd("forceload add -32 -32 32 32");
        GhastTest.spawnPlayer();
        cmd("tp GhastTester 0.5 -58 0.5");
        String harness = "equipment:{body:{id:\"minecraft:white_harness\",count:1}}";
        cmd("summon minecraft:happy_ghast 0.5 -20 0.5 {Tags:[\"t.a\"]," + harness + "}");
        cmd("summon minecraft:happy_ghast 10.5 -20 0.5 {Tags:[\"t.b\"]," + harness + "}");
        cmd("summon minecraft:happy_ghast -10.5 -20 0.5 {Tags:[\"t.c\"]}");
        cmd("summon minecraft:happy_ghast 0.5 -20 10.5 {Tags:[\"t.z\"]," + harness + "}");
        cmd("summon minecraft:ghast 0.5 -20 -12.5 {Tags:[\"t.h\"],PersistenceRequired:1b}");
        cmd("summon minecraft:zombie 3.5 -60 3.5 {Tags:[\"t.zombie\"],PersistenceRequired:1b}");
        tick(80);

        // 1. load defaults
        check("default speed is 2×", get("storage full_ghast_ahead:config speed").equals("200"), get("storage full_ghast_ahead:config"));
        // a narrow path: Paper cuts long /data get output short with "..."
        check("presets written", get("storage full_ghast_ahead:presets list[{label:\"4×\"}].label").contains("4×"),
            get("storage full_ghast_ahead:presets list[{label:\"4×\"}]"));

        // 2. nothing boosted while unridden
        check("unridden happy ghast stays vanilla", near(fs("t.a"), 0.05, 1e-9) && !boosted("t.a"), f(fs("t.a")));
        check("hostile ghast untouched", near(fs("t.h"), 0.06, 1e-9), f(fs("t.h")));

        // 3. mount -> boost
        cmd("ride GhastTester mount @e[tag=t.a,limit=1]");
        tick(2);
        List<String> ctl = cmd("execute as @e[tag=t.a] on controller if entity @s[type=minecraft:player]");
        check("player is the controller of the harnessed ghast", ctl.toString().contains("passed"), ctl.toString());
        check("ridden happy ghast boosted to 0.05*sqrt(2)", near(fs("t.a"), 0.05 * Math.sqrt(2), 1e-6) && boosted("t.a"), f(fs("t.a")));
        check("other happy ghast still vanilla", near(fs("t.b"), 0.05, 1e-9) && !boosted("t.b"), f(fs("t.b")));
        List<String> mod = cmd("attribute @e[tag=t.a,limit=1] minecraft:flying_speed modifier value get full_ghast_ahead:boost");
        check("modifier id full_ghast_ahead:boost present", !mod.isEmpty() && mod.get(0).contains("0.414"), mod.toString());

        // 4. ridden flight speed (client physics replay)
        double[] speeds = new double[5];
        int[] presets = {100, 150, 200, 300, 400};
        for (int i = 0; i < presets.length; i++) {
            cmd("function full_ghast_ahead:pick {speed:" + presets[i] + "}");
            reset("t.a");
            Vec3 v = fly("t.a", 0.98f, false, 80);
            speeds[i] = v.horizontalDistance();
            info(presets[i] + "%: flying_speed " + f(fs("t.a")) + " -> " + String.format("%.3f", speeds[i]) + " b/t = " + String.format("%.2f", speeds[i] * 20) + " blocks/s (dy " + String.format("%.4f", v.y) + ")");
        }
        for (int i = 1; i < presets.length; i++) {
            double ratio = speeds[i] / speeds[0];
            check("forward speed ratio " + presets[i] + "%", near(ratio, presets[i] / 100.0, 0.02 * presets[i] / 100.0), String.format("%.3f", ratio));
        }
        cmd("function full_ghast_ahead:pick {speed:100}");
        reset("t.a");
        double up1 = fly("t.a", 0, true, 80).y;
        cmd("function full_ghast_ahead:pick {speed:200}");
        reset("t.a");
        double up2 = fly("t.a", 0, true, 80).y;
        check("climb speed also 2×", up1 > 0 && near(up2 / up1, 2.0, 0.04), String.format("%.3f -> %.3f b/t", up1, up2));
        reset("t.a");

        // 5. dismount -> vanilla
        cmd("ride GhastTester dismount");
        tick(2);
        check("dismounted ghast back to vanilla", near(fs("t.a"), 0.05, 1e-9) && !boosted("t.a"), f(fs("t.a")));
        mod = cmd("attribute @e[tag=t.a,limit=1] minecraft:flying_speed modifier value get full_ghast_ahead:boost");
        check("modifier removed on dismount", mod.isEmpty() || !mod.get(0).contains("0.414"), mod.toString());

        // 6. not player-steered -> no boost
        cmd("ride GhastTester mount @e[tag=t.c,limit=1]");
        tick(2);
        check("no harness (nobody steers) -> not boosted", near(fs("t.c"), 0.05, 1e-9) && !boosted("t.c"), f(fs("t.c")));
        cmd("ride GhastTester dismount");
        cmd("ride @e[tag=t.zombie,limit=1] mount @e[tag=t.z,limit=1]");
        cmd("tp GhastTester 0.5 -58 0.5");
        tick(1);
        cmd("ride GhastTester mount @e[tag=t.z,limit=1]");
        tick(2);
        boolean playerFront = on(() -> find("t.z").getFirstPassenger() == GhastTest.player);
        check("player joining a mob rider takes the front seat and gets the boost", playerFront && near(fs("t.z"), 0.05 * Math.sqrt(2), 1e-6), f(fs("t.z")));
        cmd("ride GhastTester dismount");
        tick(2);
        check("mob-only rider -> not boosted", near(fs("t.z"), 0.05, 1e-9) && !boosted("t.z"), f(fs("t.z")));
        cmd("ride GhastTester dismount");
        cmd("ride GhastTester mount @e[tag=t.h,limit=1]");
        tick(2);
        check("riding a hostile ghast -> untouched", near(fs("t.h"), 0.06, 1e-9), f(fs("t.h")));
        cmd("ride GhastTester dismount");
        tick(2);

        // 7. settings menu
        GhastTest.chat();
        cmd("execute as GhastTester run function full_ghast_ahead:settings");
        List<String> menu = GhastTest.chat();
        for (String m : menu) info("menu: " + m);
        String row = menu.stream().filter(m -> m.contains("Ridden speed")).findFirst().orElse("");
        check("menu row lists 6 presets, current one not clickable", row.contains("[1×] [1.5×] [2×] [2.5×] [3×] [4×]") && row.contains("{clicks=5}"), row);
        check("menu has version and uninstall", menu.stream().anyMatch(m -> m.contains("v1.0.0")) && menu.stream().anyMatch(m -> m.contains("[Uninstall]") && m.contains("{clicks=1}")), "");
        check("menu storage cleaned", get("storage full_ghast_ahead:menu row").isEmpty(), get("storage full_ghast_ahead:menu"));

        // 8. change setting while riding
        cmd("ride GhastTester mount @e[tag=t.a,limit=1]");
        tick(2);
        cmd("execute as GhastTester run function full_ghast_ahead:set {speed:300}");
        check("setting 3× re-boosts the ridden ghast at once", near(fs("t.a"), 0.05 * Math.sqrt(3), 1e-6), f(fs("t.a")));
        List<String> menu2 = GhastTest.chat();
        check("menu re-rendered after click", menu2.stream().anyMatch(m -> m.contains("Ridden speed") && m.contains("{clicks=5}")), menu2.toString());
        cmd("execute as GhastTester run function full_ghast_ahead:set {speed:123}");
        GhastTest.chat();
        check("unknown preset ignored", get("storage full_ghast_ahead:config speed").equals("300") && near(fs("t.a"), 0.05 * Math.sqrt(3), 1e-6), get("storage full_ghast_ahead:config"));

        // 9. reload keeps the setting and the boost
        cmd("reload");
        tick(3);
        check("setting survives /reload", get("storage full_ghast_ahead:config speed").equals("300"), get("storage full_ghast_ahead:config"));
        check("ridden ghast still boosted after /reload", near(fs("t.a"), 0.05 * Math.sqrt(3), 1e-6) && boosted("t.a"), f(fs("t.a")));

        // 10. uninstall
        cmd("execute as GhastTester run function full_ghast_ahead:uninstall");
        tick(3);
        check("uninstall resets ridden ghast", near(fs("t.a"), 0.05, 1e-9) && !boosted("t.a"), f(fs("t.a")));
        check("uninstall clears storage", get("storage full_ghast_ahead:config").equals("{}") && get("storage full_ghast_ahead:presets").equals("{}"), get("storage full_ghast_ahead:config") + " " + get("storage full_ghast_ahead:presets"));
        cmd("reload");
        tick(3);
        check("reload after uninstall reinstalls at 2×", near(fs("t.a"), 0.05 * Math.sqrt(2), 1e-6) && get("storage full_ghast_ahead:config speed").equals("200"), f(fs("t.a")));
        cmd("ride GhastTester dismount");
        tick(2);
    }
}
