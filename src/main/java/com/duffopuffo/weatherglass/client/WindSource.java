package com.duffopuffo.weatherglass.client;

import com.duffopuffo.weatherglass.WeatherGlass;
import com.duffopuffo.weatherglass.WeatherGlassConfig;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Formatting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.math.Direction;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector3f;

public final class WindSource {
   public static final String PARTICLE_RAIN = "Particle Rain";
   public static final String WINDLINK = "WindLink";
   private static final float PARTICLE_RAIN_FULL = 0.033839997F;
   private static final float WINDLINK_FULL = 0.4F;
   private static boolean detected;
   private static final List<String> FOUND = new ArrayList<>();
   private static MethodHandle particleRainWind;
   private static Field particleRainParticles;
   private static Field windStrength;
   private static Field stormWindStrength;
   private static MethodHandle windLinkTarget;
   private static MethodHandle windLinkKnown;
   private static MethodHandle windLinkVx;
   private static MethodHandle windLinkVz;
   private static boolean announced;
   private static int ticks;
   private static float rainX;
   private static float rainZ;
   private static float dustX;
   private static float dustZ;
   private static boolean windOn = true;
   private static boolean calmed;

   private WindSource() {
   }

   public static List<String> found() {
      detect();
      return FOUND;
   }

   private static synchronized void detect() {
      if (!detected) {
         detected = true;
         Lookup lookup = MethodHandles.publicLookup();

         try {
            Class<?> data = Class.forName("com.plunderpixels.wind.api.PlunderWindData");
            Class<?> sample = Class.forName("com.plunderpixels.windsource.WindSample");
            windLinkTarget = lookup.findStatic(
                  data, "targetFor", MethodType.methodType(sample, String.class, World.class, double.class, double.class, double.class)
               )
               .asType(MethodType.methodType(Object.class, String.class, World.class, double.class, double.class, double.class));
            windLinkKnown = lookup.findVirtual(sample, "known", MethodType.methodType(boolean.class))
               .asType(MethodType.methodType(boolean.class, Object.class));
            windLinkVx = lookup.findVirtual(sample, "vx", MethodType.methodType(double.class)).asType(MethodType.methodType(double.class, Object.class));
            windLinkVz = lookup.findVirtual(sample, "vz", MethodType.methodType(double.class)).asType(MethodType.methodType(double.class, Object.class));
            FOUND.add("WindLink");
         } catch (LinkageError | ReflectiveOperationException var4) {
            windLinkTarget = null;
         }

         try {
            Class<?> mod = Class.forName("pigcart.particlerain.ParticleRain");
            particleRainWind = lookup.findStatic(mod, "getWind", MethodType.methodType(Vector3f.class, double.class, double.class, double.class));
            particleRainParticles = Class.forName("pigcart.particlerain.ParticleLoader").getField("particles");
            Class<?> particle = Class.forName("pigcart.particlerain.config.ParticleData");
            windStrength = particle.getField("windStrength");
            stormWindStrength = particle.getField("stormWindStrength");
            FOUND.add("Particle Rain");
         } catch (LinkageError | ReflectiveOperationException var3) {
            particleRainWind = null;
         }

         if (!FOUND.isEmpty()) {
            WeatherGlass.LOG.info("Weather Glass follows the wind of {}", String.join(" and ", FOUND));
         }
      }
   }

   public static void tick(MinecraftClient mc, WeatherGlassConfig config) {
      detect();
      ClientWorld level = mc.world;
      PlayerEntity player = mc.player;
      if (level != null && player != null && !FOUND.isEmpty()) {
         announce(mc, config);
         if (particleRainWind != null && ticks++ % 20 == 0) {
            ParticleRainStreaks.tick(config.rainOn());
         }

         if (windOn && !config.wind) {
            rainX = 0.0F;
            rainZ = 0.0F;
            dustX = 0.0F;
            dustZ = 0.0F;
            calmed = true;
         }

         windOn = config.wind;
         float[] rain = config.wind ? sample(level, player, "rain", 0.4F, 0.8F) : new float[2];
         float[] dust = config.wind ? sample(level, player, "dust", 0.7F, 1.0F) : new float[2];
         rainX = rainX + (rain[0] - rainX) * 0.05F;
         rainZ = rainZ + (rain[1] - rainZ) * 0.05F;
         dustX = dustX + (dust[0] - dustX) * 0.01F;
         dustZ = dustZ + (dust[1] - dustZ) * 0.01F;
      }
   }

   private static float[] sample(ClientWorld level, PlayerEntity player, String id, float calm, float storm) {
      try {
         if (windLinkTarget != null) {
            Object sample = (Object)windLinkTarget.invokeExact(
               (String)id, (World)level, (double)player.getX(), (double)player.getY(), (double)player.getZ()
            );
            if (sample != null && (boolean)windLinkKnown.invokeExact((Object)sample)) {
               return new float[]{
                  limit((float)(double)windLinkVx.invokeExact((Object)sample) / 0.4F), limit((float)(double)windLinkVz.invokeExact((Object)sample) / 0.4F)
               };
            }
         }

         if (particleRainWind != null) {
            Vector3f wind = (Vector3f)particleRainWind.invokeExact((double)player.getX(), (double)player.getY(), (double)player.getZ());
            float strength = strength(id, level.isThundering(), calm, storm);
            return new float[]{limit(wind.x * strength / 0.033839997F), limit(wind.z * strength / 0.033839997F)};
         }
      } catch (Throwable var7) {
         WeatherGlass.LOG.warn("Could not read the wind, Weather Glass goes on without it", var7);
         windLinkTarget = null;
         particleRainWind = null;
      }

      return new float[2];
   }

   private static float strength(String id, boolean thunder, float calm, float storm) {
      try {
         if (particleRainParticles.get(null) instanceof Map<?, ?> particles
            && particles.get(id) != null
            && (thunder ? stormWindStrength : windStrength).get(particles.get(id)) instanceof Float value) {
            return value;
         }
      } catch (ReflectiveOperationException var7) {
      }

      return thunder ? storm : calm;
   }

   private static float limit(float value) {
      return Math.max(-1.5F, Math.min(1.5F, value));
   }

   private static void announce(MinecraftClient mc, WeatherGlassConfig config) {
      if (!announced) {
         announced = true;
         List<String> fresh = new ArrayList<>();

         for (String name : FOUND) {
            if (!config.announcedWind.contains(name)) {
               fresh.add(name);
            }
         }

         if (!fresh.isEmpty()) {
            for (String namex : fresh) {
               String key = namex.equals("Particle Rain") ? "weatherglass.wind.found.particlerain" : "weatherglass.wind.found";
               mc.inGameHud
                  .getChatHud()
                  .addMessage(
                     Text.literal("[Weather Glass] ")
                        .formatted(Formatting.GREEN)
                        .append(Text.translatable(key, new Object[]{namex}).formatted(Formatting.GREEN))
                  );
            }

            WeatherGlass.LOG.info("Told the player Weather Glass follows the wind of {}", String.join(" and ", fresh));
            List<String> all = new ArrayList<>(config.announcedWind);
            all.addAll(fresh);
            config.announcedWind = List.copyOf(all);
            config.save();
         }
      }
   }

   public static boolean calmed() {
      boolean was = calmed;
      calmed = false;
      return was;
   }

   public static float rainShare(Direction side) {
      return 1.0F + 0.8F * Math.max(-1.0F, Math.min(1.0F, into(side, rainX, rainZ)));
   }

   public static float rainLean(Direction side) {
      Direction right = side.rotateYCounterclockwise();
      return Math.max(-1.0F, Math.min(1.0F, rainX * right.getOffsetX() + rainZ * right.getOffsetZ()));
   }

   public static float dustShare(Direction side) {
      float strength = Math.min(1.0F, (float)Math.sqrt(dustX * dustX + dustZ * dustZ));
      if (strength < 0.001F) {
         return 1.0F;
      } else {
         float into = into(side, dustX, dustZ) / strength;
         return 1.0F - 0.3F * strength * (1.0F - into);
      }
   }

   private static float into(Direction side, float x, float z) {
      return -(x * side.getOffsetX() + z * side.getOffsetZ());
   }

   public static void announceAgain() {
      announced = false;
      WeatherGlassConfig.get().announcedWind = List.of();
   }

   public static String describe() {
      return String.format("rain wind %.2f %.2f, dust wind %.2f %.2f", rainX, rainZ, dustX, dustZ);
   }
}
