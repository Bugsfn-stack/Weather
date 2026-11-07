package com.duffopuffo.weatherglass;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public final class WeatherGlassConfig {
   public static final int OFF = 0;
   public static final int LIGHT = 1;
   public static final int NORMAL = 2;
   public static final int HEAVY = 3;
   public static final int STYLE_DROPS = 0;
   public static final int STYLE_STREAKS = 1;
   private static final Path FILE = Path.of("config", "weatherglass.properties");
   private static volatile WeatherGlassConfig instance;
   public volatile int rainAmount = 2;
   public volatile int rainStyle = 0;
   public volatile int rainPixels = 16;
   public volatile int snowAmount = 2;
   public volatile int snowPixels = 16;
   public volatile boolean insideFrost = true;
   public volatile boolean breath = true;
   public volatile int dustAmount = 2;
   public volatile int dustPixels = 16;
   public volatile boolean wind = true;
   public volatile List<String> announcedWind = List.of();

   private WeatherGlassConfig() {
   }

   public static WeatherGlassConfig get() {
      WeatherGlassConfig config = instance;
      if (config == null) {
         synchronized (WeatherGlassConfig.class) {
            config = instance;
            if (config == null) {
               config = load();
               instance = config;
            }
         }
      }

      return config;
   }

   public boolean rainOn() {
      return this.rainAmount != 0;
   }

   public boolean snowOn() {
      return this.snowAmount != 0;
   }

   public boolean dustOn() {
      return this.dustAmount != 0;
   }

   public boolean enabled() {
      return this.rainOn() || this.snowOn() || this.dustOn();
   }

   public float rainFactor() {
      return factor(this.rainAmount);
   }

   public float snowFactor() {
      return factor(this.snowAmount);
   }

   public float dustFactor() {
      return switch (this.dustAmount) {
         case 0 -> 0.0F;
         case 1 -> 0.5F;
         default -> 1.0F;
         case 3 -> 1.4F;
      };
   }

   private static float factor(int amount) {
      return switch (amount) {
         case 0 -> 0.0F;
         case 1 -> 0.5F;
         default -> 1.0F;
         case 3 -> 1.7F;
      };
   }

   private static WeatherGlassConfig load() {
      WeatherGlassConfig config = new WeatherGlassConfig();
      if (Files.exists(FILE)) {
         Properties props = new Properties();

         try (Reader reader = Files.newBufferedReader(FILE)) {
            props.load(reader);
         } catch (IOException var7) {
            WeatherGlass.LOG.warn("Could not read {}, using defaults", FILE, var7);
         }

         config.rainAmount = parseInt(props, "rain_amount", parseInt(props, "amount", config.rainAmount));
         config.rainStyle = parseInt(props, "rain_style", parseInt(props, "style", config.rainStyle));
         config.rainPixels = parseInt(props, "rain_pixels", config.rainPixels);
         boolean oldSnow = Boolean.parseBoolean(props.getProperty("snow", "true").trim());
         config.snowAmount = parseInt(props, "snow_amount", oldSnow ? config.snowAmount : 0);
         config.snowPixels = parseInt(props, "snow_pixels", config.snowPixels);
         config.insideFrost = Boolean.parseBoolean(props.getProperty("inside_frost", "true").trim());
         config.breath = Boolean.parseBoolean(props.getProperty("breath", "true").trim());
         config.dustAmount = parseInt(props, "dust_amount", config.dustAmount);
         config.dustPixels = parseInt(props, "dust_pixels", config.dustPixels);
         config.wind = Boolean.parseBoolean(props.getProperty("wind", "true").trim());
         config.announcedWind = Arrays.stream(props.getProperty("announced_wind", "").split(",")).map(String::trim).filter(name -> !name.isEmpty()).toList();
         config.clampAll();
         if (!props.containsKey("rain_amount") || !props.containsKey("dust_amount")) {
            config.save();
         }
      } else {
         config.save();
      }

      return config;
   }

   private static int parseInt(Properties props, String key, int fallback) {
      try {
         return Integer.parseInt(props.getProperty(key, Integer.toString(fallback)).trim());
      } catch (NumberFormatException var4) {
         return fallback;
      }
   }

   public void clampAll() {
      this.rainAmount = Math.max(0, Math.min(3, this.rainAmount));
      this.rainStyle = Math.max(0, Math.min(1, this.rainStyle));
      this.rainPixels = pixels(this.rainPixels);
      this.snowAmount = Math.max(0, Math.min(3, this.snowAmount));
      this.snowPixels = pixels(this.snowPixels);
      this.dustAmount = Math.max(0, Math.min(3, this.dustAmount));
      this.dustPixels = pixels(this.dustPixels);
   }

   private static int pixels(int value) {
      return value >= 64 ? 64 : (value >= 32 ? 32 : 16);
   }

   public void save() {
      Properties props = new Properties();
      props.setProperty("rain_amount", Integer.toString(this.rainAmount));
      props.setProperty("rain_style", Integer.toString(this.rainStyle));
      props.setProperty("rain_pixels", Integer.toString(this.rainPixels));
      props.setProperty("snow_amount", Integer.toString(this.snowAmount));
      props.setProperty("snow_pixels", Integer.toString(this.snowPixels));
      props.setProperty("inside_frost", Boolean.toString(this.insideFrost));
      props.setProperty("breath", Boolean.toString(this.breath));
      props.setProperty("dust_amount", Integer.toString(this.dustAmount));
      props.setProperty("dust_pixels", Integer.toString(this.dustPixels));
      props.setProperty("wind", Boolean.toString(this.wind));
      props.setProperty("announced_wind", String.join(",", this.announcedWind));

      try {
         Files.createDirectories(FILE.getParent());

         try (Writer writer = Files.newBufferedWriter(FILE)) {
            props.store(
               writer,
               "Weather Glass. rain_amount / snow_amount: 0 off, 1 light, 2 normal, 3 heavy; rain_style: 0 drops, 1 streaks; rain_pixels / snow_pixels: 16, 32 or 64 per block; inside_frost: frost on the inside of windows too; breath: breathing on cold glass fogs it a little; dust_amount: 0 off .. 3 heavy, dust storms in hot biomes; dust_pixels: 16, 32 or 64; wind: follow the wind of Particle Rain or WindLink; announced_wind: the mods already named in the chat"
            );
         }
      } catch (IOException var7) {
         WeatherGlass.LOG.warn("Could not write {}", FILE, var7);
      }
   }
}
