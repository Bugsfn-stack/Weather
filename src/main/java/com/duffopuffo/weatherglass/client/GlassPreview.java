package com.duffopuffo.weatherglass.client;

import com.duffopuffo.weatherglass.WeatherGlassConfig;
import net.minecraft.client.gl.RenderPipelines;
import java.util.Arrays;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class GlassPreview implements AutoCloseable {
   private static final int BACK = 32;
   private static final int DUST_CYCLE = 240;
   private static int created;
   private final GlassPreview.Kind kind;
   private final boolean snow;
   private final Identifier backId;
   private final Identifier filmId;
   private final NativeImageBackedTexture back;
   private NativeImageBackedTexture film;
   private int filmSize;
   private final RainSurface[] rains = new RainSurface[FilmSprites.PIXELS.length];
   private final SnowSurface[] snows = new SnowSurface[FilmSprites.PIXELS.length];
   private int ticks;
   private byte[] layer = new byte[0];
   private byte[] frost = new byte[0];

   public GlassPreview(GlassPreview.Kind kind) {
      this.kind = kind;
      this.snow = kind == GlassPreview.Kind.SNOW;
      int n = ++created;
      this.backId = Identifier.of("weatherglass", "preview/back_" + n);
      this.filmId = Identifier.of("weatherglass", "preview/film_" + n);
      this.back = new NativeImageBackedTexture("weatherglass_preview_back", 32, 32, true);
      neutral(this.back.getImage());
      this.back.upload();
      MinecraftClient.getInstance().getTextureManager().registerTexture(this.backId, this.back);
   }

   public void tick(WeatherGlassConfig config) {
      this.ticks++;
      int res = FilmSprites.res(this.kind == GlassPreview.Kind.DUST ? config.dustPixels : (this.snow ? config.snowPixels : config.rainPixels));
      int pixels = FilmSprites.PIXELS[res];
      int size = pixels * 2;
      if (this.film == null || this.filmSize != size) {
         if (this.film != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(this.filmId);
         }

         this.film = new NativeImageBackedTexture("weatherglass_preview_film", size, size, true);
         this.filmSize = size;
         MinecraftClient.getInstance().getTextureManager().registerTexture(this.filmId, this.film);
      }

      if (this.layer.length < pixels * pixels * 4) {
         this.layer = new byte[pixels * pixels * 4];
         this.frost = new byte[pixels * pixels * 4];
      }

      NativeImage image = this.film.getImage();
      if (this.kind == GlassPreview.Kind.DUST) {
         float t = this.ticks % 240 / 240.0F;
         float peak = config.dustOn() ? Math.min(1.0F, (this.ticks / 240 % 2 == 0 ? 0.5F : 1.0F) * config.dustFactor()) : 0.0F;
         float dust = t < 0.25F ? peak * t / 0.25F : (t < 0.42F ? peak : (t < 0.83F ? peak * (1.0F - (t - 0.42F) / 0.41F) : 0.0F));

         for (int column = 0; column < 2; column++) {
            for (int row = 0; row < 2; row++) {
               int mask = (row == 0 ? 1 : 2) | (column == 0 ? 4 : 8);
               DustPattern.draw(this.layer, pixels, mask, dust, DustPattern.SAND);
               put(image, this.layer, pixels, column, row, true);
            }
         }
      } else if (!this.snow) {
         if (this.rains[res] == null) {
            this.rains[res] = new RainSurface(RainSurface.Detail.of(pixels));
         }

         RainSurface rain = this.rains[res];
         rain.tick(1.0F, config.rainFactor(), config.rainStyle);

         for (int column = 0; column < 2; column++) {
            byte[] strip = rain.pixels(column);

            for (int row = 0; row < 2; row++) {
               blockOf(strip, row, pixels, this.layer);
               put(image, this.layer, pixels, column, row, true);
            }
         }
      } else {
         if (this.snows[res] == null) {
            this.snows[res] = new SnowSurface(pixels);
         }

         SnowSurface flakes = this.snows[res];
         flakes.tick(config.snowOn() ? 1.0F : 0.0F, config.snowFactor());
         float t = this.ticks % 120 / 120.0F;
         float fog = !config.breath || !config.snowOn() ? 0.0F : (t < 0.08F ? t / 0.08F : Math.max(0.0F, 1.0F - (t - 0.08F) / 0.62F));

         for (int column = 0; column < 2; column++) {
            byte[] strip = flakes.pixels(column);

            for (int row = 0; row < 2; row++) {
               int mask = (row == 0 ? 1 : 2) | (column == 0 ? 4 : 8);
               if (config.snowOn()) {
                  FrostPattern.draw(this.frost, pixels, mask, 0.55F + 0.25F * config.snowFactor() / 1.7F, 1.0F);
               } else {
                  Arrays.fill(this.frost, (byte)0);
               }

               put(image, this.frost, pixels, column, row, true);
               blockOf(strip, row, pixels, this.layer);
               put(image, this.layer, pixels, column, row, false);
               if (fog > 0.0F) {
                  BreathFog.drawPatch(this.layer, pixels, column, row, 1.0F, 1.0F, fog, 77);
                  put(image, this.layer, pixels, column, row, false);
               }
            }
         }
      }

      this.film.upload();
   }

   private static void blockOf(byte[] strip, int row, int pixels, byte[] out) {
      System.arraycopy(strip, row * pixels * pixels * 4, out, 0, pixels * pixels * 4);
   }

   private static void put(NativeImage image, byte[] rgba, int pixels, int column, int row, boolean first) {
      for (int y = 0; y < pixels; y++) {
         for (int x = 0; x < pixels; x++) {
            int o = (y * pixels + x) * 4;
            int a = rgba[o + 3] & 255;
            int ix = column * pixels + x;
            int iy = row * pixels + y;
            if (first) {
               set(image, ix, iy, a << 24 | (rgba[o] & 255) << 16 | (rgba[o + 1] & 255) << 8 | rgba[o + 2] & 255);
            } else if (a > 0) {
               int under = get(image, ix, iy);
               int ua = under >>> 24;
               int outA = a + ua * (255 - a) / 255;
               int r = blend(rgba[o] & 255, under >> 16 & 0xFF, a, ua, outA);
               int g = blend(rgba[o + 1] & 255, under >> 8 & 0xFF, a, ua, outA);
               int b = blend(rgba[o + 2] & 255, under & 0xFF, a, ua, outA);
               set(image, ix, iy, outA << 24 | r << 16 | g << 8 | b);
            }
         }
      }
   }

   private static int blend(int top, int under, int a, int ua, int outA) {
      return outA == 0 ? 0 : (top * a + under * ua * (255 - a) / 255) / outA;
   }

   public void draw(DrawContext graphics, int x, int y, int size) {
      graphics.fill(x - 2, y - 2, x + size + 2, y + size + 2, -15461356);
      graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, -3613466);
      graphics.drawTexture(RenderPipelines.GUI_TEXTURED, this.backId, x, y, 0.0F, 0.0F, size, size, 32, 32, 32, 32, -1);
      Sprite glass = MinecraftClient.getInstance().getAtlasManager().getAtlasTexture(Identifier.ofVanilla("blocks")).getSprite(Identifier.ofVanilla("block/glass"));
      int atlasScale = 16384;
      int glassW = Math.max(1, Math.round((glass.getMaxU() - glass.getMinU()) * atlasScale));
      int glassH = Math.max(1, Math.round((glass.getMaxV() - glass.getMinV()) * atlasScale));
      int half = size / 2;

      for (int i = 0; i < 4; i++) {
         graphics.drawTexture(RenderPipelines.GUI_TEXTURED, SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, x + i % 2 * half, y + i / 2 * half, glass.getMinU() * atlasScale, glass.getMinV() * atlasScale, half, half, glassW, glassH, atlasScale, atlasScale, -1);
      }

      if (this.film != null) {
         graphics.drawTexture(RenderPipelines.GUI_TEXTURED, this.filmId, x, y, 0.0F, 0.0F, size, size, this.filmSize, this.filmSize, this.filmSize, this.filmSize, -1);
      }
   }

   private static void set(NativeImage image, int x, int y, int argb) {
      image.setColorArgb(x, y, argb);
   }

   private static int get(NativeImage image, int x, int y) {
      return image.getColorArgb(x, y);
   }

   @Override
   public void close() {
      MinecraftClient.getInstance().getTextureManager().destroyTexture(this.backId);
      if (this.film != null) {
         MinecraftClient.getInstance().getTextureManager().destroyTexture(this.filmId);
      }
   }

   private static void neutral(NativeImage image) {
      for (int y = 0; y < 32; y++) {
         int color = mix(3948358, 2763825, y / 31.0F);

         for (int x = 0; x < 32; x++) {
            set(image, x, y, 0xFF000000 | color);
         }
      }
   }

   private static int mix(int a, int b, float t) {
      int r = (int)((a >> 16 & 0xFF) * (1.0F - t) + (b >> 16 & 0xFF) * t);
      int g = (int)((a >> 8 & 0xFF) * (1.0F - t) + (b >> 8 & 0xFF) * t);
      int bl = (int)((a & 0xFF) * (1.0F - t) + (b & 0xFF) * t);
      return r << 16 | g << 8 | bl;
   }

   public static enum Kind {
      RAIN,
      SNOW,
      DUST;
   }
}
