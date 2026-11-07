package com.duffopuffo.weatherglass.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class SnowSurface {
   private static final float DT = 0.05F;
   private static final int WHITE = 16777215;
   private static final int POWDER = 15531003;
   private static final int ICE = 12375295;
   private static final int[][][] SHAPES = new int[][][]{
      {{0, 0, 16777215, 90}},
      {{0, 0, 16777215, 90}, {1, 1, 15531003, 75}},
      {{0, 0, 16777215, 95}, {-1, 0, 15531003, 80}, {1, 0, 15531003, 80}, {0, -1, 15531003, 80}, {0, 1, 15531003, 80}},
      {
            {0, 0, 16777215, 95},
            {-1, 0, 16777215, 85},
            {1, 0, 16777215, 85},
            {0, -1, 16777215, 85},
            {0, 1, 16777215, 85},
            {-2, 0, 12375295, 70},
            {2, 0, 12375295, 70},
            {0, -2, 12375295, 70},
            {0, 2, 12375295, 70},
            {-1, -1, 12375295, 60},
            {1, -1, 12375295, 60},
            {-1, 1, 12375295, 60},
            {1, 1, 12375295, 60}
      }
   };
   private final int width;
   private final int height;
   private final float k;
   private final float[] kinds;
   public static final int STRIPS = 8;
   private final SnowSurface.Strip[] strips = new SnowSurface.Strip[8];
   private boolean empty = true;

   public SnowSurface(int width) {
      this.width = width;
      this.height = width * 4;
      this.k = width / 32.0F;
      this.kinds = width >= 64
         ? new float[]{0.45F, 0.25F, 0.2F, 0.1F}
         : (width <= 16 ? new float[]{0.8F, 0.15F, 0.05F, 0.0F} : new float[]{0.6F, 0.25F, 0.12F, 0.03F});

      for (int i = 0; i < this.strips.length; i++) {
         this.strips[i] = new SnowSurface.Strip(new Random(23055L + 104729L * i + width));
      }
   }

   public int width() {
      return this.width;
   }

   public boolean tick(float snow, float amount) {
      if (amount <= 0.0F) {
         if (this.empty) {
            return false;
         }

         this.clear();
      }

      boolean any = false;

      for (SnowSurface.Strip strip : this.strips) {
         any |= strip.tick(snow, amount);
      }

      boolean changed = any || !this.empty;
      this.empty = !any;
      return changed;
   }

   public void clear() {
      for (SnowSurface.Strip strip : this.strips) {
         strip.flakes.clear();
      }
   }

   public byte[] pixels(int strip) {
      return this.strips[strip].rgba;
   }

   public int count() {
      int n = 0;

      for (SnowSurface.Strip strip : this.strips) {
         n += strip.flakes.size();
      }

      return n;
   }

   private static final class Flake {
      int x;
      int y;
      int kind;
      int life = -1;
      int lifeTotal;
   }

   private final class Strip {
      final Random random;
      final List<SnowSurface.Flake> flakes = new ArrayList<>();
      final float[] alpha = new float[SnowSurface.this.width * SnowSurface.this.height];
      final int[] color = new int[this.alpha.length];
      final byte[] rgba = new byte[this.alpha.length * 4];
      float carry;
      boolean renderedEmpty;

      Strip(Random random) {
         this.random = random;
      }

      boolean tick(float snow, float amount) {
         boolean snowing = snow > 0.05F;
         if (snowing) {
            float strength = Math.min(snow, 1.4F);
            this.carry = this.carry + 6.0F * SnowSurface.this.k * SnowSurface.this.k * amount * strength * 0.05F;
            int cap = (int)(140.0F * SnowSurface.this.k * SnowSurface.this.k * amount * strength);

            while (this.carry >= 1.0F) {
               this.carry--;
               if (this.flakes.size() < cap) {
                  this.land();
               }
            }
         }

         for (int i = this.flakes.size() - 1; i >= 0; i--) {
            SnowSurface.Flake flake = this.flakes.get(i);
            if (snowing) {
               flake.life = -1;
            } else if (flake.life < 0) {
               flake.lifeTotal = 20 * (20 + this.random.nextInt(41));
               flake.life = flake.lifeTotal;
            } else if (--flake.life <= 0) {
               this.flakes.remove(i);
            }
         }

         if (this.flakes.isEmpty()) {
            if (this.renderedEmpty) {
               return false;
            }

            this.renderedEmpty = true;
         } else {
            this.renderedEmpty = false;
         }

         return this.render();
      }

      void land() {
         float r = this.random.nextFloat();
         int kind = 0;
         float sum = SnowSurface.this.kinds[0];

         while (kind < SnowSurface.this.kinds.length - 1 && r >= sum) {
            sum += SnowSurface.this.kinds[++kind];
         }

         SnowSurface.Flake flake = new SnowSurface.Flake();
         flake.kind = kind;
         int margin = kind >= 3 ? 3 : 2;
         flake.x = margin + this.random.nextInt(SnowSurface.this.width - 2 * margin);
         flake.y = this.random.nextInt(SnowSurface.this.height);
         this.flakes.add(flake);
      }

      boolean render() {
         Arrays.fill(this.alpha, 0.0F);
         int w = SnowSurface.this.width;
         int h = SnowSurface.this.height;

         for (SnowSurface.Flake flake : this.flakes) {
            float fade = 1.0F;
            int kind = flake.kind;
            if (flake.life >= 0) {
               float left = (float)flake.life / flake.lifeTotal;
               fade = Math.min(1.0F, left / 0.4F);
               if (left < 0.25F) {
                  kind = Math.min(kind, 1);
               }
            }

            for (int[] cell : SnowSurface.SHAPES[kind]) {
               int x = flake.x + cell[0];
               if (x >= 0 && x < w) {
                  int i = Math.floorMod(flake.y + cell[1], h) * w + x;
                  float a = cell[3] / 100.0F * fade;
                  if (a > this.alpha[i]) {
                     this.alpha[i] = a;
                     this.color[i] = cell[2];
                  }
               }
            }
         }

         boolean any = false;

         for (int i = 0; i < this.alpha.length; i++) {
            float a = this.alpha[i];
            int c = a > 0.0F ? this.color[i] : 16777215;
            int o = i * 4;
            this.rgba[o] = (byte)(c >> 16);
            this.rgba[o + 1] = (byte)(c >> 8);
            this.rgba[o + 2] = (byte)c;
            this.rgba[o + 3] = (byte)Math.round(a * 255.0F);
            any |= a > 0.0F;
         }

         return any;
      }
   }
}
