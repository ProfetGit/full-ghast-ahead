package ghastride;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.phys.Vec3;

/** Dev-only: holds the forward key while the player rides a happy ghast and prints the client-side flight speed. */
public final class RideProbe {
    static final int WARM = Integer.getInteger("ride.warm", 60);
    static final int SPAN = Integer.getInteger("ride.span", 60);
    static long lastTick = Long.MIN_VALUE;
    static int ridden, frames;
    static double path;
    static Vec3 prev, start;
    static boolean done;
    static long startTime;
    static final StringBuilder trace = new StringBuilder();

    public static void onFrame(Minecraft mc) {
        if (done || mc.level == null || mc.player == null) return;
        if (++frames > 20000) { finish(mc, "[ghastride] never mounted"); return; }
        Entity v = mc.player.getVehicle();
        if (!(v instanceof HappyGhast g)) return;
        mc.options.keyUp.setDown(true);
        long t = g.tickCount;
        if (t == lastTick) return;
        lastTick = t;
        ridden++;
        Vec3 pos = g.position();
        if (ridden == WARM) { start = pos; path = 0; startTime = mc.level.getGameTime(); }
        else if (ridden > WARM && prev != null) path += pos.subtract(prev).horizontalDistance();
        if (prev != null) trace.append(String.format(" %d:%.3f%s", ridden, pos.subtract(prev).horizontalDistance(), mc.player.zza == 0 ? "!" : ""));
        prev = pos;
        if (ridden == WARM + SPAN) {
            System.out.println("[ghastride] trace" + trace.substring(Math.max(0, trace.length() - 300)));
            System.out.println("[ghastride] game time advanced " + (mc.level.getGameTime() - startTime) + " over " + SPAN + " ghast ticks");
            Vec3 d = pos.subtract(start);
            finish(mc, String.format("[ghastride] client flying_speed=%.5f path=%.2f blocks/s straight=%.2f blocks/s dy=%.3f yaw=%.1f pitch=%.1f",
                g.getAttributeValue(Attributes.FLYING_SPEED), path / SPAN * 20, d.horizontalDistance() / SPAN * 20, d.y, g.getYRot(), mc.player.getXRot()));
        }
    }

    static void finish(Minecraft mc, String msg) {
        done = true;
        mc.options.keyUp.setDown(false);
        System.out.println(msg);
        mc.stop();
    }
}
