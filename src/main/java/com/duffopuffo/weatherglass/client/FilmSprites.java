package com.duffopuffo.weatherglass.client;

import com.duffopuffo.weatherglass.WeatherGlass;
import com.duffopuffo.weatherglass.WeatherGlassConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.texture.NativeImage;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Direction.Type;
import org.lwjgl.system.MemoryUtil;

public final class FilmSprites {
   public static final int RAIN_COUNT = 64;
   public static final int SNOW_COUNT = 32;
   public static final int DUST_COUNT = 64;
   public static final int[] PIXELS = new int[]{16, 32, 64};
   private static final Identifier[][] RAIN = new Identifier[][]{strips("rain16_", 64), strips("rain_", 64), strips("rain64_", 64)};
   private static final Identifier[][] SNOW = new Identifier[][]{strips("snow16_", 32), strips("snow_", 32), strips("snow64_", 32)};
   private static final Identifier[][] FROST = new Identifier[][]{frosts("frost16_"), frosts("frost_"), frosts("frost64_")};
   private static final Identifier[][] FROST_INSIDE = new Identifier[][]{frosts("frostin16_"), frosts("frostin_"), frosts("frostin64_")};
   private static final Identifier[][] BREATH = new Identifier[][]{breaths("breath16_"), breaths("breath_"), breaths("breath64_")};
   private static final Identifier[][] DUST = new Identifier[][]{dusts("dust16_"), dusts("dust_"), dusts("dust64_")};
   private static final Identifier[][] RED_DUST = new Identifier[][]{dusts("reddust16_"), dusts("reddust_"), dusts("reddust64_")};
   private static final int NEAR = 4;
   private static final int FORGET = 40;
   private static final Set<Long> MOVING = ConcurrentHashMap.newKeySet();
   private static boolean near;
   private static final float[] SHARE = new float[4];
   private static final float[] LEAN = new float[4];
   private static final RainSurface[] RAIN_SURFACES = new RainSurface[]{
      new RainSurface(RainSurface.Detail.PIXELS_16), new RainSurface(RainSurface.Detail.PIXELS_32), new RainSurface(RainSurface.Detail.PIXELS_64)
   };
   private static final SnowSurface[] SNOW_SURFACES = new SnowSurface[]{new SnowSurface(16), new SnowSurface(32), new SnowSurface(64)};
   private static final ByteBuffer[] LEVELS = new ByteBuffer[8];
   private static final byte[][] MIPS = new byte[8][];
   public static final int SHOW_RAIN = 0;
   public static final int SHOW_SNOW = 1;
   public static final int SHOW_DUST = 2;
   private static final List<Set<Long>> EXPOSED = List.of(ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet());
   private static final boolean[] SHOWN = new boolean[3];
   private static final int[] REMARK = new int[3];
   private static final Map<Identifier[], boolean[]> STALE = new IdentityHashMap<>();
   private static int frame;
   public static volatile boolean rainFilms;
   public static volatile boolean snowFilms;
   public static volatile boolean frostFilms;
   public static volatile boolean dustFilms;
   public static volatile boolean redDustFilms;
   private static int lastRainPixels;
   private static int lastSnowPixels;
   private static int lastDustPixels;
   private static byte[] breathPixels;
   private static Sprite lastProbe;
   private static int maxLevel;
   private static ClientWorld frostLevel;
   private static float frost;
   private static float snowLedge;
   private static float drawnFrost = -1.0F;
   private static float drawnLedge = -1.0F;
   private static byte[] frostPixels;
   private static float dust;
   private static final float[] DRAWN_DUST = new float[]{-1.0F, -1.0F, -1.0F, -1.0F};
   private static ClientWorld dustLevel;
   private static boolean sandDrawn;
   private static boolean redSandDrawn;
   private static boolean warned;
   public static long nanos;
   public static int ticks;

   private FilmSprites() {
   }

   private static Identifier[] strips(String prefix, int count) {
      Identifier[] ids = new Identifier[count];

      for (int i = 0; i < count; i++) {
         ids[i] = Identifier.of("weatherglass", "block/" + prefix + i / 4 + "_" + i % 4);
      }

      return ids;
   }

   private static Identifier[] frosts(String prefix) {
      Identifier[] ids = new Identifier[16];

      for (int i = 0; i < ids.length; i++) {
         ids[i] = Identifier.of("weatherglass", "block/" + prefix + i);
      }

      return ids;
   }

   private static Identifier[] dusts(String prefix) {
      Identifier[] ids = new Identifier[64];

      for (int i = 0; i < ids.length; i++) {
         ids[i] = Identifier.of("weatherglass", "block/" + prefix + i / 16 + "_" + i % 16);
      }

      return ids;
   }

   private static Identifier[] breaths(String prefix) {
      Identifier[] ids = new Identifier[8];

      for (int i = 0; i < ids.length; i++) {
         ids[i] = Identifier.of("weatherglass", "block/" + prefix + i);
      }

      return ids;
   }

   public static int res(int pixels) {
      return pixels >= 64 ? 2 : (pixels <= 16 ? 0 : 1);
   }

   public static Identifier breathId(int pixels, int slot) {
      return BREATH[res(pixels)][slot];
   }

   public static Identifier rainId(int pixels, int index) {
      return RAIN[res(pixels)][index];
   }

   public static Identifier snowId(int pixels, int index) {
      return SNOW[res(pixels)][index];
   }

   public static Identifier frostId(int pixels, int mask) {
      return FROST[res(pixels)][mask];
   }

   public static Identifier dustId(int pixels, boolean red, int index) {
      return (red ? RED_DUST : DUST)[res(pixels)][index];
   }

   public static float dust() {
      return dust;
   }

   public static void setDust(float value) {
      dust = value;
      Arrays.fill(DRAWN_DUST, -1.0F);
   }

   public static Identifier insideFrostId(int pixels, int mask) {
      return FROST_INSIDE[res(pixels)][mask];
   }

   public static int rainIndex(int x, int y, int z, Direction side) {
      Direction right = side.rotateYCounterclockwise();
      int along = x * right.getOffsetX() + z * right.getOffsetZ();
      int wall = wall(side.getAxis() == Axis.X ? x : z, side);
      int panel = WindSource.found().isEmpty() ? wall & 1 : side.getHorizontalQuarterTurns();
      int strip = panel * 4 + Math.floorMod(along + (wall >>> 1), 4);
      return strip * 4 + Math.floorMod(-y + (wall >>> 3), 4);
   }

   private static int wall(int plane, Direction side) {
      int h = plane * 522133279 ^ (side.getIndex() + 1) * 668265261;
      h ^= h >>> 15;
      h *= 739982445;
      h ^= h >>> 12;
      return h >>> 8;
   }

   public static int snowIndex(int x, int y, int z, Direction side) {
      int strip = column(x, z, side) & 7;
      return strip * 4 + Math.floorMod(-y, 4);
   }

   private static int column(int x, int z, Direction side) {
      int h = x * 522133279 ^ z * 1540483477 ^ (side.getIndex() + 1) * 668265261;
      h ^= h >>> 15;
      h *= 739982445;
      h ^= h >>> 12;
      return h >>> 8;
   }

   public static RainSurface surface() {
      return RAIN_SURFACES[res(WeatherGlassConfig.get().rainPixels)];
   }

   public static SnowSurface snowSurface() {
      return SNOW_SURFACES[res(WeatherGlassConfig.get().snowPixels)];
   }

   public static float frost() {
      return frost;
   }

   public static void exposed(int kind, BlockPos pos) {
      EXPOSED.get(kind).add(ChunkSectionPos.toLong(pos));
   }

   public static boolean shown(int kind) {
      synchronized (SHOWN) {
         return SHOWN[kind];
      }
   }

   private static void show(MinecraftClient mc, int kind, boolean value) {
      synchronized (SHOWN) {
         if (SHOWN[kind] == value) {
            if (REMARK[kind] > 0 && --REMARK[kind] == 0) {
               markExposed(mc, kind);
            }

            return;
         }

         SHOWN[kind] = value;
      }

      REMARK[kind] = 20;
      markExposed(mc, kind);
   }

   private static void markExposed(MinecraftClient mc, int kind) {
      for (long key : EXPOSED.get(kind)) {
         mc.worldRenderer.scheduleChunkRender(ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackY(key), ChunkSectionPos.unpackZ(key));
      }
   }

   public static void moving(BlockPos pos) {
      MOVING.add(ChunkSectionPos.toLong(pos));
   }

   public static boolean near() {
      return near;
   }

   private static int distance(long key, int x, int y, int z) {
      return Math.max(
         Math.abs(ChunkSectionPos.unpackX(key) - x), Math.max(Math.abs(ChunkSectionPos.unpackY(key) - y), Math.abs(ChunkSectionPos.unpackZ(key) - z))
      );
   }

   private static boolean filmsNear(MinecraftClient mc) {
      if (mc.player == null) {
         return false;
      } else {
         int x = ChunkSectionPos.getSectionCoord(mc.player.getBlockX());
         int y = ChunkSectionPos.getSectionCoord(mc.player.getBlockY());
         int z = ChunkSectionPos.getSectionCoord(mc.player.getBlockZ());
         boolean found = false;
         Iterator<Long> it = MOVING.iterator();

         while (it.hasNext()) {
            int d = distance(it.next(), x, y, z);
            if (d > 40) {
               it.remove();
            } else if (d <= 4) {
               found = true;
            }
         }

         for (Set<Long> sections : EXPOSED) {
            sections.removeIf(key -> distance(key, x, y, z) > 40);
         }

         return found;
      }
   }

   public static void tick(MinecraftClient mc) {
      ClientWorld level = mc.world;
      if (level != null && !mc.isPaused()) {
         long start = System.nanoTime();
         WeatherGlassConfig config = WeatherGlassConfig.get();
         SpriteAtlasTexture atlas = MinecraftClient.getInstance().getAtlasManager().getAtlasTexture(Identifier.ofVanilla("blocks"));
         Sprite probe = atlas.getSprite(RAIN[0][0]);
         boolean forceRain = false;
         boolean forceSnow = false;
         if (probe != lastProbe) {
            lastProbe = probe;
            WeatherGlass.LOG.info("[diag] DEBUG_STATIC={} film probe sprite {} missing={} size={}x{} at {},{}",
               DEBUG_STATIC, RAIN[0][0], probe == atlas.getMissingSprite(), probe.getContents().getWidth(), probe.getContents().getHeight(), probe.getX(), probe.getY());
            target = atlas.getGlTexture();
            maxLevel = target.getMipLevels() - 1;
            lastRainPixels = 0;
            lastSnowPixels = 0;
            lastDustPixels = 0;
            sandDrawn = false;
            redSandDrawn = false;
         }

         if (config.rainPixels != lastRainPixels) {
            for (RainSurface surface : RAIN_SURFACES) {
               surface.clear();
            }

            lastRainPixels = config.rainPixels;
            forceRain = true;
         }

         if (config.snowPixels != lastSnowPixels) {
            for (SnowSurface surface : SNOW_SURFACES) {
               surface.clear();
            }

            lastSnowPixels = config.snowPixels;
            forceSnow = true;
            BreathFog.markAllStale();
         }

         int rainPixels = PIXELS[res(config.rainPixels)];
         int pixels = PIXELS[res(config.snowPixels)];
         float weather = level.getRainGradient(1.0F) * (1.0F + 0.4F * level.getThunderGradient(1.0F));
         WindSource.tick(mc, config);
         if (ticks % 20 == 0 || forceRain || forceSnow) {
            near = filmsNear(mc);
         }

         if (rainFilms && config.rainOn() && near) {
            for (Direction side : Type.HORIZONTAL) {
               SHARE[side.getHorizontalQuarterTurns()] = WindSource.rainShare(side);
               LEAN[side.getHorizontalQuarterTurns()] = WindSource.rainLean(side);
            }

            RainSurface rain = surface();
            if (WindSource.calmed()) {
               rain.evenOut();
            }

            int used = WindSource.found().isEmpty() ? 8 : 16;
            if (rain.tick(weather, config.rainFactor(), config.rainStyle, SHARE, LEAN, used) || forceRain) {
               boolean all = forceRain;
               uploadStrips(atlas, RAIN[res(rainPixels)], rainPixels, rain::pixels, strip -> all || rain.changed(strip), all);
            }
         }

         if (config.snowOn() && (snowFilms || frostFilms)) {
            if (snowFilms && near) {
               SnowSurface snow = snowSurface();
               if (snow.tick(weather, config.snowFactor()) || forceSnow) {
                  uploadStrips(atlas, SNOW[res(pixels)], pixels, snow::pixels, strip -> true, forceSnow);
               }
            }

            if (frostFilms) {
               updateFrost(level, weather, forceSnow || level != frostLevel);
               frostLevel = level;
               if (forceSnow || Math.abs(frost - drawnFrost) > 0.015625F || Math.abs(snowLedge - drawnLedge) > 0.03125F) {
                  uploadFrost(atlas, pixels);
               }
            }
         }

         if (config.dustOn() && (dustFilms || redDustFilms)) {
            if (config.dustPixels != lastDustPixels) {
               lastDustPixels = config.dustPixels;
               sandDrawn = false;
               redSandDrawn = false;
            }

            boolean forceDust = dustFilms && !sandDrawn || redDustFilms && !redSandDrawn;
            updateDust(level, config, level != dustLevel);
            dustLevel = level;
            uploadDust(atlas, PIXELS[res(config.dustPixels)], forceDust);
         }

         show(mc, 0, rainFilms && config.rainOn() && (weather > 0.05F || !surface().dry()));
         show(mc, 1, snowFilms && config.snowOn() && (weather > 0.05F || snowSurface().count() > 0));
         show(mc, 2, (dustFilms || redDustFilms) && config.dustOn() && dust > 0.015625F);
         frame++;
         BreathFog.tick(mc);
         uploadBreath(atlas, pixels);
         nanos = nanos + (System.nanoTime() - start);
         ticks++;
         if (ticks % 600 == 1) {
            WeatherGlass.LOG.info("[diag] tick {}: rainGradient={} flags rain={} snow={} frost={} dust={} filmQuads rain={} snow={} frost={} dust={} enabled={} rainOn={} snowOn={}",
               ticks, level.getRainGradient(1.0F), rainFilms, snowFilms, frostFilms, dustFilms,
               FilmLayout.FILMS.get(), FilmLayout.SNOW_FILMS.get(), FilmLayout.FROST_FILMS.get(), FilmLayout.DUST_FILMS.get(),
               config.enabled(), config.rainOn(), config.snowOn());
         }
      }
   }

   private static void updateFrost(ClientWorld level, float weather, boolean jump) {
      boolean snowing = weather > 0.05F;
      float target = snowing ? 1.0F : (level.isNight() ? 0.7F : 0.45F);
      float ledgeTarget = snowing ? 1.0F : 0.0F;
      if (jump) {
         frost = target;
         snowLedge = snowing ? 1.0F : 0.0F;
      } else {
         frost = frost < target ? Math.min(target, frost + 5.5555557E-4F) : Math.max(target, frost - 3.3333333E-4F);
         snowLedge = snowLedge < ledgeTarget ? Math.min(1.0F, snowLedge + 8.3333335E-4F) : Math.max(0.0F, snowLedge - 2.0833334E-4F);
      }
   }

   private static void updateDust(ClientWorld level, WeatherGlassConfig config, boolean jump) {
      float storm = level.getRainGradient(1.0F);
      float target = storm > 0.05F ? Math.min(1.0F, (level.getThunderGradient(1.0F) > 0.5F ? 1.0F : 0.5F) * config.dustFactor()) : 0.0F;
      if (jump) {
         dust = target;
      } else {
         dust = dust < target ? Math.min(target, dust + 8.3333335E-4F) : Math.max(target, dust - 2.7777778E-4F);
      }
   }

   private static void uploadDust(SpriteAtlasTexture atlas, int size0, boolean force) {
      boolean bound = false;

      for (Direction side : Type.HORIZONTAL) {
         int s = side.getHorizontalQuarterTurns();
         float amount = dust * WindSource.dustShare(side);
         if (force || !(Math.abs(amount - DRAWN_DUST[s]) <= 0.015625F)) {
            if (!bound) {
               bind(atlas);
               bound = true;
               if (frostPixels == null || frostPixels.length < size0 * size0 * 4) {
                  frostPixels = new byte[size0 * size0 * 4];
               }
            }

            for (int red = 0; red < 2; red++) {
               if (red == 1 ? redDustFilms : dustFilms) {
                  for (int mask = 0; mask < 16; mask++) {
                     DustPattern.draw(frostPixels, size0, mask, amount, red == 1 ? DustPattern.RED_SAND : DustPattern.SAND);
                     byte[] level0 = mip(0, size0);
                     System.arraycopy(frostPixels, 0, level0, 0, size0 * size0 * 4);
                     upload(atlas, dustId(size0, red == 1, s * 16 + mask), size0, level0);
                  }
               }
            }

            DRAWN_DUST[s] = amount;
         }
      }

      if (force) {
         sandDrawn = sandDrawn | dustFilms;
         redSandDrawn = redSandDrawn | redDustFilms;
      }
   }

   private static void uploadStrips(SpriteAtlasTexture atlas, Identifier[] ids, int size0, FilmSprites.Pixels pixels, IntPredicate changed, boolean force) {
      bind(atlas);
      int bytes = size0 * size0 * 4;
      int levels = !force && frame % 4 != 0 ? (frame % 2 == 0 ? 2 : 1) : Integer.MAX_VALUE;
      boolean[] stale = STALE.computeIfAbsent(ids, k -> new boolean[k.length]);

      for (int i = 0; i < ids.length; i++) {
         if (changed.test(i / 4) || stale[i] && levels == Integer.MAX_VALUE) {
            stale[i] = levels != Integer.MAX_VALUE;
            byte[] level0 = mip(0, size0);
            System.arraycopy(pixels.strip(i / 4), i % 4 * bytes, level0, 0, bytes);
            upload(atlas, ids[i], size0, level0, levels);
         }
      }
   }

   private static void uploadFrost(SpriteAtlasTexture atlas, int size0) {
      bind(atlas);
      if (frostPixels == null || frostPixels.length < size0 * size0 * 4) {
         frostPixels = new byte[size0 * size0 * 4];
      }

      for (int inside = 0; inside < 2; inside++) {
         Identifier[] ids = (inside == 1 ? FROST_INSIDE : FROST)[res(size0)];

         for (int mask = 0; mask < ids.length; mask++) {
            FrostPattern.draw(frostPixels, size0, mask, frost, inside == 1 ? 0.0F : snowLedge);
            byte[] level0 = mip(0, size0);
            System.arraycopy(frostPixels, 0, level0, 0, size0 * size0 * 4);
            upload(atlas, ids[mask], size0, level0);
         }
      }

      drawnFrost = frost;
      drawnLedge = snowLedge;
   }

   private static void uploadBreath(SpriteAtlasTexture atlas, int size0) {
      boolean bound = false;

      for (int slot = 0; slot < 8; slot++) {
         if (BreathFog.takeStale(slot)) {
            if (!bound) {
               bind(atlas);
               bound = true;
               if (breathPixels == null || breathPixels.length < size0 * size0 * 4) {
                  breathPixels = new byte[size0 * size0 * 4];
               }
            }

            BreathFog.draw(slot, size0, breathPixels);
            byte[] level0 = mip(0, size0);
            System.arraycopy(breathPixels, 0, level0, 0, size0 * size0 * 4);
            upload(atlas, BREATH[res(size0)][slot], size0, level0);
         }
      }
   }

   private static GpuTexture target;

   private static void bind(SpriteAtlasTexture atlas) {
      target = atlas.getGlTexture();
   }

   private static void upload(SpriteAtlasTexture atlas, Identifier id, int size0, byte[] level0) {
      upload(atlas, id, size0, level0, Integer.MAX_VALUE);
   }

   private static void upload(SpriteAtlasTexture atlas, Identifier id, int size0, byte[] level0, int levels) {
      Sprite sprite = atlas.getSprite(id);
      if (sprite == atlas.getMissingSprite()) {
         if (!warned) {
            warned = true;
            WeatherGlass.LOG.warn("Film sprite {} is missing from the block atlas (textures not packaged?), it will not show", id);
         }

         return;
      }

      int x = sprite.getX();
      int y = sprite.getY();
      if (sprite.getContents().getWidth() == size0 && sprite.getContents().getHeight() == size0) {
         write(0, x, y, size0, level0);
         int level = 1;

         for (int size = size0 / 2; level <= maxLevel && level < levels && size >= 1; size /= 2) {
            byte[] half = mip(level, size);
            halve(MIPS[level - 1], size * 2, half);
            write(level, x >> level, y >> level, size, half);
            level++;
         }
      } else {
         if (!warned) {
            warned = true;
            WeatherGlass.LOG.warn("Film sprite {} is not {}x{} in the block atlas (resource pack?), it will not show", new Object[]{id, size0, size0});
         }
      }
   }

   private static final boolean DEBUG_STATIC = FilmSprites.class.getResource("/assets/weatherglass/debug_films.txt") != null;

   private static void write(int level, int x, int y, int size, byte[] pixels) {
      if (DEBUG_STATIC) {
         return;
      }

      ByteBuffer buffer = buffer(level, size);
      buffer.put(pixels, 0, size * size * 4);
      buffer.flip();
      RenderSystem.getDevice().createCommandEncoder().writeToTexture(target, buffer, NativeImage.Format.RGBA, level, 0, x, y, size, size);
   }

   private static byte[] mip(int level, int size) {
      byte[] pixels = MIPS[level];
      if (pixels == null || pixels.length < size * size * 4) {
         pixels = new byte[size * size * 4];
         MIPS[level] = pixels;
      }

      return pixels;
   }

   private static ByteBuffer buffer(int level, int size) {
      ByteBuffer buffer = LEVELS[level];
      if (buffer == null || buffer.capacity() < size * size * 4) {
         if (buffer != null) {
            MemoryUtil.memFree(buffer);
         }

         buffer = MemoryUtil.memAlloc(size * size * 4);
         LEVELS[level] = buffer;
      }

      buffer.clear();
      return buffer;
   }

   private static void halve(byte[] src, int srcSize, byte[] dst) {
      int size = srcSize / 2;
      int out = 0;

      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            int r = 0;
            int g = 0;
            int b = 0;
            int a = 0;
            int max = 0;

            for (int dy = 0; dy < 2; dy++) {
               int o = ((y * 2 + dy) * srcSize + x * 2) * 4;

               for (int dx = 0; dx < 2; o += 4) {
                  int pa = src[o + 3] & 255;
                  r += (src[o] & 255) * pa;
                  g += (src[o + 1] & 255) * pa;
                  b += (src[o + 2] & 255) * pa;
                  a += pa;
                  max = Math.max(max, pa);
                  dx++;
               }
            }

            if (a > 0) {
               dst[out] = (byte)((r + a / 2) / a);
               dst[out + 1] = (byte)((g + a / 2) / a);
               dst[out + 2] = (byte)((b + a / 2) / a);
            } else {
               dst[out] = src[0];
               dst[out + 1] = src[1];
               dst[out + 2] = src[2];
            }

            dst[out + 3] = (byte)Math.max((a + 2) / 4, max * 4 / 5);
            out += 4;
         }
      }
   }

   private interface Pixels {
      byte[] strip(int var1);
   }
}
