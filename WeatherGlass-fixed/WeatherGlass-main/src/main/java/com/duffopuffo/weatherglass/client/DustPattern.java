package com.duffopuffo.weatherglass.client;

public final class DustPattern {
   public static final int[][] SAND = new int[][]{{219, 207, 163}, {213, 196, 150}, {227, 218, 176}, {200, 181, 132}, {176, 154, 106}};
   public static final int[][] RED_SAND = new int[][]{{191, 103, 33}, {178, 96, 31}, {205, 113, 40}, {160, 84, 30}, {132, 70, 26}};

   private DustPattern() {
   }

   public static void draw(byte[] out, int size, int mask, float dust, int[][] palette) {
      if (size <= 16) {
         drawVanilla(out, mask, dust, palette);
      } else {
         float k = size / 32.0F;
         float reach = 0.1F + 0.9F * dust;

         for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
               int o = (y * size + x) * 4;
               int[] c = null;
               float a = 0.0F;
               float fy = (float)y / (size - 1);
               if (dust > 0.02F) {
                  float edge = edgeDistance(mask, x, y, size) / k / 32.0F;
                  float cover = reach - edge * 2.4F + 0.15F * dust * fy * fy + 0.55F * (blotch(x / k, y / k) - 0.5F);
                  float n = 0.6F * hash((int)(x / (2.0F * k)), (int)(y / (2.0F * k)), 13) + 0.4F * hash(x, y, 17);
                  if (n < cover) {
                     c = palette[(int)(hash(x, y, 29) * 4.0F) & 3];
                     a = 0.55F + 0.3F * Math.min(1.0F, Math.max(0.0F, cover));
                  }

                  if (hash(x, y, 41) < 0.042F * dust || hash(x - 1, y, 41) < 0.042F * dust * 0.25F) {
                     c = palette[(int)(hash(x, y, 53) * 4.0F) & 3];
                     a = 0.9F;
                  }

                  if ((mask & 2) != 0) {
                     float drift = driftHeight(mask, x / k, dust) * k;
                     float fromBottom = size - 1 - y;
                     if (fromBottom < drift) {
                        c = fromBottom < k ? palette[4] : (fromBottom > drift - k ? palette[2] : palette[(int)(hash(x, y, 67) * 3.0F)]);
                        a = 0.97F;
                     }
                  }
               }

               if (c == null) {
                  out[o] = (byte)palette[0][0];
                  out[o + 1] = (byte)palette[0][1];
                  out[o + 2] = (byte)palette[0][2];
                  out[o + 3] = 0;
               } else {
                  out[o] = (byte)c[0];
                  out[o + 1] = (byte)c[1];
                  out[o + 2] = (byte)c[2];
                  out[o + 3] = (byte)Math.round(a * 255.0F);
               }
            }
         }
      }
   }

   private static void drawVanilla(byte[] out, int mask, float dust, int[][] palette) {
      for (int y = 0; y < 16; y++) {
         for (int x = 0; x < 16; x++) {
            int o = (y * 16 + x) * 4;
            int[] c = palette[0];
            float a = 0.0F;
            if (dust > 0.02F) {
               float edge = edgeDistance(mask, x, y, 16);
               a = 0.16F * dust + 0.45F * dust * (float)Math.exp(-edge / 2.0F);
               float depth = 1.0F + 2.5F * dust + (hash(x / 2, y / 2, 13) - 0.5F) * 2.0F;
               if (edge < depth) {
                  c = palette[(int)(hash(x, y, 29) * 4.0F) & 3];
                  a = 0.75F;
               } else if (hash(x, y, 41) < 0.05F * dust) {
                  c = palette[(int)(hash(x, y, 53) * 4.0F) & 3];
                  a = 0.9F;
               }

               if ((mask & 2) != 0) {
                  int full = 2 + (hash(x / 3, 0, 71) > 0.6F ? 1 : 0);
                  if ((mask & 4) != 0) {
                     full = Math.max(full, 5 - x / 2);
                  }

                  if ((mask & 8) != 0) {
                     full = Math.max(full, 5 - (15 - x) / 2);
                  }

                  int height = Math.round(full * Math.min(1.0F, dust * 1.4F));
                  int fromBottom = 15 - y;
                  if (fromBottom < height) {
                     c = fromBottom == height - 1 ? palette[2] : (fromBottom == 0 && height > 2 ? palette[3] : palette[(int)(hash(x, fromBottom, 67) * 2.0F)]);
                     a = 0.97F;
                  }
               }
            }

            out[o] = (byte)c[0];
            out[o + 1] = (byte)c[1];
            out[o + 2] = (byte)c[2];
            out[o + 3] = (byte)Math.round(Math.min(1.0F, a) * 255.0F);
         }
      }
   }

   private static float edgeDistance(int mask, int x, int y, int size) {
      float d = 2.0F * size;
      if ((mask & 1) != 0) {
         d = Math.min(d, (float)y);
      }

      if ((mask & 2) != 0) {
         d = Math.min(d, (size - 1 - y) * 0.5F);
      }

      if ((mask & 4) != 0) {
         d = Math.min(d, (float)x);
      }

      if ((mask & 8) != 0) {
         d = Math.min(d, (float)(size - 1 - x));
      }

      return d;
   }

   private static float driftHeight(int mask, float x, float dust) {
      float corner = 0.0F;
      if ((mask & 4) != 0) {
         corner = Math.max(corner, 1.0F - x / 10.0F);
      }

      if ((mask & 8) != 0) {
         corner = Math.max(corner, 1.0F - (31.0F - x) / 10.0F);
      }

      float bump = 0.5F + 0.5F * (float)Math.sin(x * 0.55F + 1.3F);
      float height = 1.5F + 2.5F * dust + 1.5F * bump + (2.0F + 5.0F * dust) * (float)Math.pow(corner, 1.5) + hash((int)x, 0, 71);
      return height * Math.min(1.0F, dust * 3.0F);
   }

   private static float blotch(float x, float y) {
      int x0 = (int)Math.floor(x / 8.0F);
      int y0 = (int)Math.floor(y / 8.0F);
      float fx = x / 8.0F - x0;
      float fy = y / 8.0F - y0;
      float top = hash(x0, y0, 5) * (1.0F - fx) + hash(x0 + 1, y0, 5) * fx;
      float bottom = hash(x0, y0 + 1, 5) * (1.0F - fx) + hash(x0 + 1, y0 + 1, 5) * fx;
      return top * (1.0F - fy) + bottom * fy;
   }

   private static float hash(int x, int y, int salt) {
      int h = x * 668265261 ^ y * 374761393 ^ salt * -1640531527;
      h ^= h >>> 15;
      h *= -2048144789;
      h ^= h >>> 13;
      return (h & 65535) / 65535.0F;
   }
}
