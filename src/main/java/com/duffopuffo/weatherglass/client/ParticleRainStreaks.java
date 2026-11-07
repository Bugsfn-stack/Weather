package com.duffopuffo.weatherglass.client;

import com.duffopuffo.weatherglass.WeatherGlass;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.Identifier;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.Registries;

public final class ParticleRainStreaks {
   private static final Set<String> GLASS_TAGS = Set.of(
      "minecraft:impermeable", "c:glass_panes", "forge:glass_panes", "c:glass_blocks", "c:glass_blocks/colorless", "forge:glass"
   );
   private static boolean looked;
   private static Field particles;
   private static Field blockList;
   private static Field tags;
   private static Field ids;
   private static Field whitelist;
   private static Method populate;
   private static boolean off;

   private ParticleRainStreaks() {
   }

   private static boolean lookUp() {
      if (!looked) {
         looked = true;

         try {
            particles = Class.forName("pigcart.particlerain.ParticleLoader").getField("particles");
            blockList = Class.forName("pigcart.particlerain.config.ParticleData").getField("blockList");
            Class<?> list = Class.forName("pigcart.particlerain.config.Whitelist");
            tags = list.getField("tags");
            ids = list.getField("ids");
            whitelist = list.getField("isWhitelist");
            populate = list.getMethod("populateInternalLists");
         } catch (LinkageError | ReflectiveOperationException var1) {
            WeatherGlass.LOG.info("Could not reach Particle Rain's rain streaks, they stay on glass too");
            particles = null;
         }
      }

      return particles != null;
   }

   static void tick(boolean rainOn) {
      if (lookUp() && (rainOn || off)) {
         try {
            if (!(particles.get(null) instanceof Map<?, ?> all && all.get("streak") != null)) {
               return;
            }

            Object list = blockList.get(all.get("streak"));
            if (list == null || !whitelist.getBoolean(list)) {
               return;
            }

            if (!rainOn) {
               populate.invoke(list);
               off = false;
               return;
            }

            boolean removed = tags.get(list) instanceof List<?> tagList
               && tagList.removeIf(tag -> tag instanceof TagKey<?> key && GLASS_TAGS.contains(key.id().toString()));
            removed |= ids.get(list) instanceof List<?> idList
               && idList.removeIf(id -> id instanceof Identifier name && GlassBlocks.all().contains(Registries.BLOCK.get(name)));
            if (removed) {
               WeatherGlass.LOG.info("Particle Rain's rain streaks left off the glass: Weather Glass draws its own drops there");
            }

            off = true;
         } catch (RuntimeException | ReflectiveOperationException var6) {
            WeatherGlass.LOG.warn("Could not take glass out of Particle Rain's rain streaks", var6);
            particles = null;
         }
      }
   }
}
