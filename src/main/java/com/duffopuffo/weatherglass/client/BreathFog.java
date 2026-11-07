package com.duffopuffo.weatherglass.client;

import com.duffopuffo.weatherglass.WeatherGlassConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class BreathFog {
   public static final int SLOTS = 8;
   private static final double REACH = 1.2;
   private static final float RX = 0.3F;
   private static final float RY = 0.24F;
   private static final int BREATH = 56;
   private static final float GAIN = 0.45F;
   private static final int RISE = 10;
   private static final float FADE = 0.01F;
   private static final float FADE_BREATHING = 0.0034999999F;
   private static final float SAME = 0.2F;
   private static final Map<BreathFog.Key, Integer> SLOT_OF = new ConcurrentHashMap<>();
   private static final BreathFog.Patch[] OWNER = new BreathFog.Patch[8];
   private static final BreathFog.Cover[] COVER = new BreathFog.Cover[8];
   private static final List<BreathFog.Patch> PATCHES = new ArrayList<>();
   private static final boolean[] STALE = new boolean[8];
   private static ClientWorld lastLevel;
   private static int ticks;
   private static int seeds;

   private BreathFog() {
   }

   public static int slot(BlockPos pos, Direction side) {
      if (SLOT_OF.isEmpty()) {
         return -1;
      } else {
         Integer slot = SLOT_OF.get(new BreathFog.Key(pos, side));
         return slot == null ? -1 : slot;
      }
   }

   public static int patches() {
      return PATCHES.size();
   }

   public static float strongest() {
      float max = 0.0F;

      for (BreathFog.Patch patch : PATCHES) {
         max = Math.max(max, patch.fog);
      }

      return max;
   }

   public static void tick(MinecraftClient mc) {
      ClientWorld level = mc.world;
      if (level != lastLevel) {
         for (BreathFog.Patch patch : new ArrayList<>(PATCHES)) {
            remove(patch, null);
         }

         lastLevel = level;
      }

      if (level != null && !mc.isPaused()) {
         ticks++;
         WeatherGlassConfig config = WeatherGlassConfig.get();
         BreathFog.Patch current = config.breath && config.snowOn() ? aim(mc, level) : null;

         for (BreathFog.Patch patch : new ArrayList<>(PATCHES)) {
            if (patch == current) {
               if (!patch.breathing) {
                  patch.breathing = true;
                  patch.nextBreath = Math.min(patch.nextBreath, ticks + 8);
               }

               if (ticks >= patch.nextBreath) {
                  patch.rise = 10;
                  patch.nextBreath = ticks + 56 + level.getRandom().nextInt(20);
               }
            } else {
               patch.breathing = false;
            }

            if (patch.rise > 0) {
               patch.rise--;
               patch.fog = Math.min(1.0F, patch.fog + 0.044999998F);
            } else {
               patch.fog = patch.fog - (patch.breathing ? 0.0034999999F : 0.01F);
            }

            if (patch.fog <= 0.0F && !patch.breathing) {
               remove(patch, mc);
            } else {
               for (BreathFog.Cover cover : patch.covers) {
                  STALE[cover.slot()] = true;
               }
            }
         }
      }
   }

   private static BreathFog.Patch aim(MinecraftClient mc, ClientWorld level) {
      ClientPlayerEntity player = mc.player;
      if (player != null && !player.isSpectator()) {
         Vec3d eye = player.getEyePos();
         Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(1.2));
         BlockHitResult hit = level.raycast(new RaycastContext(eye, end, ShapeType.OUTLINE, FluidHandling.NONE, player));
         if (hit.getType() != Type.BLOCK) {
            return null;
         } else {
            Direction side = hit.getSide();
            BlockPos pos = hit.getBlockPos().toImmutable();
            if (side.getAxis().isHorizontal() && GlassBlocks.is(level.getBlockState(pos))) {
               int weather = RainExposure.check(level, pos, side);
               if ((weather & 1) != 0 && (weather & 4) != 0) {
                  Vec3d local = hit.getPos().subtract(pos.getX(), pos.getY(), pos.getZ());

                  float u = switch (side) {
                     case SOUTH -> (float)local.x;
                     case NORTH -> (float)(1.0 - local.x);
                     case EAST -> (float)(1.0 - local.z);
                     default -> (float)local.z;
                  };
                  float v = (float)(1.0 - local.y);

                  for (BreathFog.Patch patch : PATCHES) {
                     if (patch.side == side
                        && plane(patch.pos, side) == plane(pos, side)
                        && Math.abs(across(patch.pos, side) + patch.u - across(pos, side) - u) < 0.2F
                        && Math.abs(patch.v - patch.pos.getY() - v + pos.getY()) < 0.2F) {
                        return patch;
                     }
                  }

                  return start(mc, level, new BreathFog.Patch(pos, side, u, v, ++seeds * -1640531527));
               } else {
                  return null;
               }
            } else {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private static int plane(BlockPos pos, Direction side) {
      return side.getAxis() == Axis.Z ? pos.getZ() : pos.getX();
   }

   private static int across(BlockPos pos, Direction side) {
      return switch (side) {
         case SOUTH -> pos.getX();
         case NORTH -> -pos.getX();
         case EAST -> -pos.getZ();
         default -> pos.getZ();
      };
   }

   private static BreathFog.Patch start(MinecraftClient mc, ClientWorld level, BreathFog.Patch patch) {
      List<int[]> offsets = new ArrayList<>();

      for (int du = patch.u - 0.3F < 0.0F ? -1 : 0; du <= (patch.u + 0.3F > 1.0F ? 1 : 0); du++) {
         for (int dv = patch.v - 0.24F < 0.0F ? -1 : 0; dv <= (patch.v + 0.24F > 1.0F ? 1 : 0); dv++) {
            offsets.add(new int[]{du, dv});
         }
      }

      Direction right = patch.side.rotateYCounterclockwise();

      for (int[] offset : offsets) {
         BlockPos pos = patch.pos.offset(right, offset[0]).down(offset[1]);
         if (GlassBlocks.is(level.getBlockState(pos))) {
            int slot = freeSlot(mc);
            if (slot < 0) {
               break;
            }

            BreathFog.Cover cover = new BreathFog.Cover(pos, offset[0], offset[1], slot);
            patch.covers.add(cover);
            OWNER[slot] = patch;
            COVER[slot] = cover;
            STALE[slot] = true;
            SLOT_OF.put(new BreathFog.Key(pos, patch.side), slot);
            mc.worldRenderer.scheduleBlockRenders(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
         }
      }

      PATCHES.add(patch);
      return patch;
   }

   private static int freeSlot(MinecraftClient mc) {
      for (int slot = 0; slot < 8; slot++) {
         if (OWNER[slot] == null) {
            return slot;
         }
      }

      BreathFog.Patch faintest = null;

      for (BreathFog.Patch patch : PATCHES) {
         if (!patch.breathing && (faintest == null || patch.fog < faintest.fog)) {
            faintest = patch;
         }
      }

      if (faintest == null) {
         return -1;
      } else {
         remove(faintest, mc);
         return freeSlot(mc);
      }
   }

   private static void remove(BreathFog.Patch patch, MinecraftClient mc) {
      for (BreathFog.Cover cover : patch.covers) {
         SLOT_OF.remove(new BreathFog.Key(cover.pos(), patch.side));
         OWNER[cover.slot()] = null;
         COVER[cover.slot()] = null;
         STALE[cover.slot()] = true;
         if (mc != null) {
            BlockPos pos = cover.pos();
            mc.worldRenderer.scheduleBlockRenders(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
         }
      }

      PATCHES.remove(patch);
   }

   static boolean takeStale(int slot) {
      boolean stale = STALE[slot];
      STALE[slot] = false;
      return stale;
   }

   static void markAllStale() {
      Arrays.fill(STALE, true);
   }

   static void draw(int slot, int size, byte[] out) {
      Arrays.fill(out, 0, size * size * 4, (byte)0);
      BreathFog.Patch patch = OWNER[slot];
      BreathFog.Cover cover = COVER[slot];
      if (patch != null && cover != null && !(patch.fog <= 0.0F)) {
         drawPatch(out, size, cover.du(), cover.dv(), patch.u, patch.v, patch.fog, patch.seed);
      }
   }

   public static void drawPatch(byte[] out, int size, int du, int dv, float u, float v, float fog, int seed) {
      float grow = 0.55F + 0.45F * fog;
      float rx = 0.3F * grow;
      float ry = 0.24F * grow;
      float strength = Math.min(1.0F, fog * 1.3F);

      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            float fu = (x + 0.5F) / size + du - u;
            float fv = (y + 0.5F) / size + dv - v;
            float r = (float)Math.sqrt(fu / rx * (fu / rx) + fv / ry * (fv / ry)) + noise(x + du * size, y + dv * size, seed);
            if (!(r >= 1.0F)) {
               int alpha = Math.round((150.0F * (1.0F - r * r) + 40.0F) * strength);
               if (alpha > 0) {
                  int o = (y * size + x) * 4;
                  out[o] = -20;
                  out[o + 1] = -14;
                  out[o + 2] = -8;
                  out[o + 3] = (byte)Math.min(255, alpha);
               }
            }
         }
      }
   }

   private static float noise(int x, int y, int seed) {
      int h = x * 668265261 ^ y * 374761393 ^ seed;
      h ^= h >>> 15;
      h *= 739982445;
      h ^= h >>> 12;
      return (h >>> 8 & 65535) / 65535.0F * 0.24F - 0.12F;
   }

   private record Cover(BlockPos pos, int du, int dv, int slot) {
   }

   private record Key(BlockPos pos, Direction side) {
   }

   private static final class Patch {
      final BlockPos pos;
      final Direction side;
      final float u;
      final float v;
      final int seed;
      final List<BreathFog.Cover> covers = new ArrayList<>();
      float fog;
      int rise;
      int nextBreath;
      boolean breathing;

      Patch(BlockPos pos, Direction side, float u, float v, int seed) {
         this.pos = pos;
         this.side = side;
         this.u = u;
         this.v = v;
         this.seed = seed;
      }
   }
}
