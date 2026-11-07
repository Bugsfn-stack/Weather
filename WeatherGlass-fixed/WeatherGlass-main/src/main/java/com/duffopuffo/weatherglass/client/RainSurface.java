package com.duffopuffo.weatherglass.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class RainSurface {
   public static final int SLICES = 4;
   public static final int PER_SIDE = 4;
   public static final int STRIPS = 16;
   public static final int SHARED = 8;
   private static final float[] EVEN = new float[]{1.0F, 1.0F, 1.0F, 1.0F};
   private static final float CATCH_UP = 6.0F;
   private static final float[] STILL = new float[4];
   private static final float DT = 0.05F;
   private static final int CELL = 16;
   private static final int BODY = rgb(109, 133, 189);
   private static final int LIGHT = rgb(232, 240, 255);
   private static final int DARK = rgb(60, 88, 166);
   private static final String[][] DRAWINGS = new String[][]{
      null,
      {"B"},
      {"LB", "DD"},
      {".L.", "LBB", ".D."},
      {".LB.", "LBBB", "BBBB", ".DD."},
      {".LBB.", "LBBBB", "BBBBB", "BBBBB", ".DDD."},
      {"..LB..", ".LBBB.", "LBBBBB", "BBBBBB", ".BBBD.", "..DD.."},
      {"..LBB..", ".LBBBB.", "LBBBBBB", "BBBBBBB", "BBBBBBD", ".BBBBD.", "..DDD.."},
      {"..LLBB..", ".LBBBBB.", "LBBBBBBB", "BBBBBBBB", "BBBBBBBB", "BBBBBBBD", ".BBBBBD.", "..DDDD.."}
   };
   private static final int[][][] SHAPES = new int[DRAWINGS.length][][];
   private static final int[][] SMALL_16 = shape(new String[]{"LB", "BD"});
   private final RainSurface.Detail detail;
   private final int width;
   private final int height;
   private final RainSurface.Panel[] panels = new RainSurface.Panel[4];
   private final byte[][] stripPixels = new byte[16][];
   private int style = -1;
   private boolean empty = true;
   private int evening;

   public RainSurface(RainSurface.Detail detail) {
      this.detail = detail;
      this.width = detail.width();
      this.height = this.width * 4;

      for (int p = 0; p < this.panels.length; p++) {
         this.panels[p] = new RainSurface.Panel(p * 4, new Random(24301L + 7919L * p + this.width));
      }

      for (int i = 0; i < 16; i++) {
         this.stripPixels[i] = new byte[this.width * this.height * 4];
      }
   }

   public int width() {
      return this.width;
   }

   public boolean tick(float rain, float amount, int style) {
      return this.tick(rain, amount, style, EVEN, STILL, 8);
   }

   public boolean tick(float rain, float amount, int style, float[] share, float[] lean, int used) {
      if (style != this.style) {
         this.clear();
         this.style = style;
      }

      if (amount <= 0.0F) {
         if (this.empty) {
            return false;
         } else {
            for (RainSurface.Panel panel : this.panels) {
               panel.clear();
               panel.render(style);
               panel.changed = true;
            }

            this.empty = true;
            return true;
         }
      } else {
         float mean = 0.0F;
         if (this.evening > 0) {
            this.evening--;

            for (int p = 0; p < used / 4; p++) {
               mean += this.panels[p].drops.size();
            }

            mean /= used / 4;
         }

         boolean wet = false;

         for (int p = 0; p < used / 4; p++) {
            RainSurface.Panel panel = this.panels[p];
            panel.share = share[p];
            panel.lean = lean[p];
            panel.catchUp = this.evening > 0 && panel.drops.size() < mean * 0.8F ? 6.0F : 1.0F;
            panel.changed = false;
            if (style == 1) {
               panel.tickStreaks(rain, amount);
            } else {
               panel.tickDrops(rain, amount);
            }

            if (panel.idle()) {
               if (!panel.renderedEmpty) {
                  panel.render(style);
                  panel.renderedEmpty = true;
                  panel.changed = true;
               }
            } else {
               panel.renderedEmpty = false;
               panel.changed = true;
               wet |= panel.render(style);
            }
         }

         boolean changed = wet || !this.empty;
         this.empty = !wet;
         return changed;
      }
   }

   public void evenOut() {
      this.evening = 100;
   }

   public boolean dry() {
      return this.empty;
   }

   public void clear() {
      for (RainSurface.Panel panel : this.panels) {
         panel.clear();
      }
   }

   public boolean changed(int strip) {
      return this.panels[strip / 4].changed;
   }

   public int count(int side) {
      return this.panels[side].drops.size() + this.panels[side].trickles.size();
   }

   public byte[] pixels(int strip) {
      return this.stripPixels[strip];
   }

   public int count() {
      int n = 0;

      for (RainSurface.Panel panel : this.panels) {
         n += panel.drops.size() + panel.trickles.size();
      }

      return n;
   }

   public int running() {
      int n = 0;

      for (RainSurface.Panel panel : this.panels) {
         for (RainSurface.Drop drop : panel.drops) {
            n += drop.running ? 1 : 0;
         }
      }

      return n;
   }

   private static int rgb(int r, int g, int b) {
      return r << 16 | g << 8 | b;
   }

   private static int[][] shape(String[] rows) {
      List<int[]> cells = new ArrayList<>();

      for (int j = 0; j < rows.length; j++) {
         for (int i = 0; i < rows[j].length(); i++) {
            switch (rows[j].charAt(i)) {
               case 'B':
                  cells.add(new int[]{i, j, 0});
                  break;
               case 'D':
                  cells.add(new int[]{i, j, 2});
                  break;
               case 'L':
                  cells.add(new int[]{i, j, 1});
            }
         }
      }

      return cells.toArray(new int[0][]);
   }

   static {
      for (int size = 1; size < DRAWINGS.length; size++) {
         SHAPES[size] = shape(DRAWINGS[size]);
      }
   }

   public record Detail(int width, float[] sizes, int maxSize, int runSize, int runStart, int runMax, int runStop, float dropsPerSecond, int maxDrops) {
      public static final RainSurface.Detail PIXELS_16 = new RainSurface.Detail(16, new float[]{0.62F, 0.38F}, 2, 2, 1, 2, 1, 6.0F, 50);
      public static final RainSurface.Detail PIXELS_32 = new RainSurface.Detail(32, new float[]{0.45F, 0.35F, 0.15F, 0.05F}, 5, 5, 2, 4, 2, 12.0F, 110);
      public static final RainSurface.Detail PIXELS_64 = new RainSurface.Detail(
         64, new float[]{0.3F, 0.28F, 0.22F, 0.12F, 0.06F, 0.02F}, 7, 7, 3, 6, 3, 26.0F, 240
      );

      public static RainSurface.Detail of(int pixels) {
         return pixels >= 64 ? PIXELS_64 : (pixels <= 16 ? PIXELS_16 : PIXELS_32);
      }

      float k() {
         return this.width / 32.0F;
      }
   }

   private static final class Drop {
      float x;
      float y;
      int size;
      float water;
      boolean running;
      float speed;
      float budget;
      boolean bright;
      int life = -1;
      int seed;
      int runAt;
      float pace = 1.0F;
      int paceTicks;
      float lean;
      float leanLeft;
      int jitter;
      int jitterLeft;
      boolean gone;
      int dryTicks = -1;
      int dryTotal;
   }

   private final class Panel {
      final Random random;
      final float[] trail = new float[RainSurface.this.width * 4 * RainSurface.this.height];
      final float[] alpha = new float[this.trail.length];
      final int[] color = new int[this.trail.length];
      final byte[] rgba = new byte[this.trail.length * 4];
      final List<RainSurface.Drop> drops = new ArrayList<>();
      final List<RainSurface.Trickle> trickles = new ArrayList<>();
      final int w = RainSurface.this.width * 4;
      final int px = RainSurface.this.width;
      final int h = RainSurface.this.height;
      final int first;
      final List<List<RainSurface.Drop>> cells = new ArrayList<>();
      final RainSurface.Detail d = RainSurface.this.detail;
      final float k = RainSurface.this.detail.k();
      float dropCarry;
      float trickleCarry;
      float beadCarry;
      boolean trailLeft;
      boolean renderedEmpty;
      boolean changed;
      float share = 1.0F;
      float lean;
      float catchUp = 1.0F;

      Panel(int first, Random random) {
         this.first = first;
         this.random = random;

         for (int c = 0; c < this.w / 16; c++) {
            this.cells.add(new ArrayList<>());
         }
      }

      void sortCells() {
         for (List<RainSurface.Drop> cell : this.cells) {
            cell.clear();
         }

         for (RainSurface.Drop drop : this.drops) {
            if (!drop.running) {
               this.cells.get(this.cell(drop.x)).add(drop);
            }
         }
      }

      int cell(float x) {
         return Math.min(this.cells.size() - 1, (int)(x / 16.0F));
      }

      void clear() {
         this.drops.clear();
         this.trickles.clear();
         Arrays.fill(this.trail, 0.0F);
         this.trailLeft = false;
         this.renderedEmpty = false;
      }

      boolean idle() {
         return this.drops.isEmpty() && this.trickles.isEmpty() && !this.trailLeft;
      }

      float range(float min, float max) {
         return min + this.random.nextFloat() * (max - min);
      }

      float wrapY(float y) {
         y %= this.h;
         return y < 0.0F ? y + this.h : y;
      }

      float deltaY(float a, float b) {
         float diff = b - a;
         if (diff > this.h / 2.0F) {
            diff -= this.h;
         } else if (diff < -this.h / 2.0F) {
            diff += this.h;
         }

         return diff;
      }

      float wrapX(float x) {
         x %= this.w;
         return x < 0.0F ? x + this.w : x;
      }

      float deltaX(float a, float b) {
         float diff = b - a;
         if (diff > this.w / 2.0F) {
            diff -= this.w;
         } else if (diff < -this.w / 2.0F) {
            diff += this.w;
         }

         return diff;
      }

      void wet(int x, int y, float value) {
         int i = Math.floorMod(y, this.h) * this.w + Math.floorMod(x, this.w);
         this.trail[i] = Math.max(this.trail[i], value);
         this.trailLeft = true;
      }

      void tickDrops(float rain, float amount) {
         boolean raining = rain > 0.05F;
         float strength = Math.min(rain, 1.4F);
         this.sortCells();
         if (raining) {
            this.dropCarry = this.dropCarry + this.d.dropsPerSecond() * 4.0F * amount * strength * this.share * this.catchUp * 0.05F;
            int cap = (int)(this.d.maxDrops() * 4 * amount * strength * this.share);

            while (this.dropCarry >= 1.0F) {
               this.dropCarry--;
               this.land(this.drops.size() < cap);
            }
         }

         for (int i = 0; i < this.drops.size(); i++) {
            RainSurface.Drop drop = this.drops.get(i);
            if (drop.running) {
               if (!raining) {
                  drop.budget = Math.min(drop.budget, 6.0F * this.k);
               }

               this.run(drop, raining);
            }
         }

         this.drops.removeIf(dropx -> dropx.gone);
         this.dry(raining);
         this.fadeTrail(1.6F);
      }

      void land(boolean room) {
         float r = this.random.nextFloat();
         int size = 1;
         float[] sizes = this.d.sizes();

         for (float sum = sizes[0]; size < sizes.length && r >= sum; size++) {
            sum += sizes[size];
         }

         RainSurface.Drop drop = new RainSurface.Drop();
         drop.size = size;
         drop.x = this.range(0.0F, this.w);
         drop.y = this.range(0.0F, this.h);
         drop.bright = this.random.nextBoolean();
         drop.seed = this.random.nextInt();
         float r2 = this.random.nextFloat();
         drop.runAt = Math.min(this.d.maxSize(), this.d.runSize() - (r2 < 0.1F ? 1 : 0));
         int cell = this.cell(drop.x);

         for (int dc = -1; dc <= 1; dc++) {
            for (RainSurface.Drop other : this.cells.get(Math.floorMod(cell + dc, this.cells.size()))) {
               if (!other.running && !other.gone && this.overlap(other, drop)) {
                  this.merge(other, drop);
                  return;
               }
            }
         }

         if (room) {
            this.drops.add(drop);
         }
      }

      boolean overlap(RainSurface.Drop a, RainSurface.Drop b) {
         float dx = this.deltaX(a.x, b.x);
         float dy = this.deltaY(a.y, b.y);
         float reach = (a.size + b.size) * 0.5F * 0.85F;
         return dx * dx + dy * dy < reach * reach;
      }

      void merge(RainSurface.Drop into, RainSurface.Drop other) {
         float a = into.water > 0.0F ? into.water : into.size * into.size;
         float b = other.water > 0.0F ? other.water : other.size * other.size;
         float share = b / (a + b);
         into.x = this.wrapX(into.x + this.deltaX(into.x, other.x) * share);
         into.y = this.wrapY(into.y + this.deltaY(into.y, other.y) * share);
         into.water = a + b;
         int merged = Math.round((float)Math.sqrt(into.water));
         into.dryTicks = -1;
         if (into.runAt == 0) {
            into.runAt = this.d.runSize();
         }

         if (into.running) {
            into.size = Math.min(this.d.runMax(), merged);
            into.budget = into.budget + 10.0F * this.k;
            into.speed = Math.min(into.speed * 1.08F, 45.0F * this.k);
         } else {
            into.size = Math.min(this.d.maxSize(), merged);
            if (into.size >= into.runAt) {
               into.running = true;
               into.size = this.d.runStart() + (this.random.nextFloat() < 0.4F ? 1 : 0);
               into.water = into.size * into.size;
               into.speed = this.range(14.0F, 30.0F) * this.k;
               into.budget = this.range(20.0F, 60.0F) * this.k;
               into.pace = 1.0F;
               into.paceTicks = 0;
               into.leanLeft = 0.0F;
            }
         }
      }

      void run(RainSurface.Drop drop, boolean raining) {
         if (--drop.paceTicks <= 0) {
            if (this.random.nextFloat() < 0.22F) {
               drop.pace = 0.0F;
               drop.paceTicks = 3 + this.random.nextInt(10);
            } else {
               drop.pace = this.range(0.45F, 1.5F);
               drop.paceTicks = 4 + this.random.nextInt(12);
            }
         }

         float move = drop.speed * drop.pace * 0.05F * (raining ? 1.0F : 0.8F);
         int fromRow = (int)Math.floor(drop.y);
         drop.y += move;
         int toRow = (int)Math.floor(drop.y);

         for (int row = fromRow + 1; row <= toRow; row++) {
            if (--drop.leanLeft <= 0.0F) {
               drop.lean = Math.max(-0.9F, Math.min(0.9F, this.range(-0.3F, 0.3F) + 0.7F * this.lean));
               drop.leanLeft = this.range(6.0F, 20.0F) * this.k;
            }

            drop.x = this.wrapX(drop.x + drop.lean);
            this.leaveTrail(drop, row);
            if (this.random.nextFloat() < (0.02F + 0.01F * drop.size) / this.k) {
               RainSurface.Drop tiny = new RainSurface.Drop();
               tiny.size = 1;
               int half = Math.max(1, Math.round(drop.size * 0.3F));
               tiny.x = this.wrapX(Math.round(drop.x) + (this.random.nextBoolean() ? half : -half));
               tiny.y = this.wrapY(row - this.runnerHeight(drop));
               tiny.bright = this.random.nextBoolean();
               tiny.runAt = this.d.runSize();
               this.drops.add(tiny);
            }

            drop.budget--;
            if (drop.budget <= 0.0F) {
               drop.size--;
               drop.water = drop.size * drop.size;
               drop.budget = this.range(20.0F, 60.0F) * this.k;
               if (drop.size < this.d.runStop()) {
                  drop.running = false;
                  drop.size = Math.max(1, drop.size);
                  drop.water = drop.size * drop.size;
                  break;
               }
            }
         }

         drop.y = this.wrapY(drop.y);
         int cell = this.cell(drop.x);

         for (int dc = -1; dc <= 1; dc++) {
            for (RainSurface.Drop other : this.cells.get(Math.floorMod(cell + dc, this.cells.size()))) {
               if (other != drop && !other.running && !other.gone && this.overlap(drop, other)) {
                  other.gone = true;
                  this.merge(drop, other);
               }
            }
         }
      }

      int runnerHeight(RainSurface.Drop drop) {
         return drop.size + Math.round(drop.size * 0.25F * Math.min(1.0F, drop.pace));
      }

      void leaveTrail(RainSurface.Drop drop, int row) {
         if (--drop.jitterLeft <= 0) {
            drop.jitter = this.random.nextInt(3) - 1;
            drop.jitterLeft = Math.round((4 + this.random.nextInt(8)) * this.k);
         }

         int width = Math.max(1, Math.round(drop.size * 0.55F) + drop.jitter);
         float wetness = 1.0F + 0.12F * (width - 1);
         int left = Math.round(drop.x - (width - 1) / 2.0F);
         int y = row - drop.size / 2;

         for (int i = 0; i < width; i++) {
            boolean edge = width >= 3 && (i == 0 || i == width - 1);
            this.wet(left + i, y, edge ? wetness * 0.6F : wetness);
         }
      }

      void dry(boolean raining) {
         for (int i = this.drops.size() - 1; i >= 0; i--) {
            RainSurface.Drop drop = this.drops.get(i);
            if (drop.life >= 0 && --drop.life <= 0) {
               this.drops.remove(i);
            } else if (raining) {
               drop.dryTicks = -1;
            } else if (!drop.running) {
               if (drop.dryTicks < 0) {
                  drop.dryTotal = (int)(20.0F * (6.0F + 20.0F * drop.size / this.d.maxSize() * this.range(0.7F, 1.0F)));
                  drop.dryTicks = drop.dryTotal;
               } else if (--drop.dryTicks <= 0) {
                  this.drops.remove(i);
               }
            }
         }
      }

      void fadeTrail(float seconds) {
         if (this.trailLeft) {
            float step = 0.05F / seconds;
            boolean left = false;

            for (int i = 0; i < this.trail.length; i++) {
               if (this.trail[i] > 0.0F) {
                  this.trail[i] = Math.max(0.0F, this.trail[i] - step);
                  left |= this.trail[i] > 0.0F;
               }
            }

            this.trailLeft = left;
         }
      }

      void tickStreaks(float rain, float amount) {
         boolean raining = rain > 0.05F;
         float strength = Math.min(rain, 1.4F);
         if (raining) {
            this.trickleCarry = this.trickleCarry + 20.0F * this.k * amount * strength * this.share * 0.05F;

            while (this.trickleCarry >= 1.0F) {
               this.trickleCarry--;
               if (this.trickles.size() < 120.0F * this.k * amount) {
                  this.startTrickle();
               }
            }

            this.beadCarry = this.beadCarry + 9.6F * this.k * this.k * amount * strength * this.share * 0.05F;

            while (this.beadCarry >= 1.0F) {
               this.beadCarry--;
               if (this.drops.size() < 96.0F * this.k * this.k * amount * strength) {
                  RainSurface.Drop bead = new RainSurface.Drop();
                  bead.size = this.k > 1.0F && this.random.nextFloat() < 0.3F ? 2 : 1;
                  bead.x = this.range(0.0F, this.w);
                  bead.y = this.range(0.0F, this.h);
                  bead.bright = true;
                  this.drops.add(bead);
               }
            }
         }

         for (int i = this.trickles.size() - 1; i >= 0; i--) {
            RainSurface.Trickle t = this.trickles.get(i);
            int fromRow = (int)Math.floor(t.y);
            t.y = t.y + t.speed * 0.05F;
            int toRow = (int)Math.floor(t.y);

            for (int row = fromRow + 1; row <= toRow && t.left > 0.0F; row++) {
               for (int dx = 0; dx < t.width; dx++) {
                  this.wet(t.x + dx, row, 1.0F);
               }

               t.left--;
               t.nextStep--;
               if (t.nextStep <= 0.0F) {
                  float wind = Math.abs(this.lean);
                  if (this.random.nextFloat() < 0.45F + 0.5F * wind) {
                     boolean right = this.random.nextFloat() < 0.5F + 0.5F * Math.signum(this.lean) * Math.min(1.0F, wind * 1.5F);
                     t.x = Math.floorMod(t.x + (right ? 1 : -1), this.w);
                  }

                  t.nextStep = this.range(10.0F, 18.0F) * this.k * (1.0F - 0.7F * Math.min(1.0F, wind));
               }
            }

            t.y = this.wrapY(t.y);
            if (t.left <= 0.0F) {
               this.trickles.remove(i);
               RainSurface.Drop bead = new RainSurface.Drop();
               bead.size = t.width;
               bead.x = this.wrapX(t.x + (t.width - 1) / 2.0F);
               bead.y = t.y;
               bead.bright = true;
               bead.life = (int)this.range(40.0F, 100.0F);
               this.drops.add(bead);
            }
         }

         this.dry(raining);
         this.fadeTrail(2.4F);
      }

      void startTrickle() {
         RainSurface.Trickle t = new RainSurface.Trickle();
         t.width = this.random.nextFloat() < 0.85F ? 1 : 2;
         t.x = this.random.nextInt(this.w);
         t.y = this.range(0.0F, this.h);
         t.speed = this.range(30.0F, 80.0F) * this.k * (1.0F + 0.25F * (t.width - 1));
         t.left = this.range(10.0F, 44.0F) * this.k;
         t.nextStep = this.range(10.0F, 18.0F) * this.k;
         this.trickles.add(t);
      }

      void put(int x, int y, int color, float a) {
         int i = Math.floorMod(y, this.h) * this.w + Math.floorMod(x, this.w);
         if (a > this.alpha[i]) {
            this.alpha[i] = a;
            this.color[i] = color;
         }
      }

      void drawRunner(RainSurface.Drop drop) {
         int width = drop.size;
         int height = this.runnerHeight(drop);
         int top = Math.round(drop.y + (width - 1) / 2.0F) - height + 1;

         for (int j = 0; j < height; j++) {
            float t = (j + 0.5F) / height;
            float u = (t - 0.65F) / 0.35F;
            float f = t < 0.65F ? 0.35F + 0.65F * (float)Math.sin(t / 0.65F * Math.PI / 2.0) : (float)Math.sqrt(1.0F - u * u * 0.64F);
            float half = width / 2.0F * f;
            float shift = ((drop.seed >>> j % 16 * 2 & 3) - 1.5F) * 0.22F;
            int left = Math.round(drop.x - half + shift);
            int right = Math.max(left, Math.round(drop.x + half + shift) - 1);

            for (int x = left; x <= right; x++) {
               if ((j != height - 1 || this.px <= 16 && x != right) && (!(t > 0.7F) || x != right || right <= left)) {
                  if (x == left && right > left && t > 0.2F && t < 0.62F) {
                     this.put(x, top + j, RainSurface.LIGHT, 0.95F);
                  } else {
                     this.put(x, top + j, RainSurface.BODY, 0.6F);
                  }
               } else {
                  this.put(x, top + j, RainSurface.DARK, 0.9F);
               }
            }
         }
      }

      boolean render(int style) {
         Arrays.fill(this.alpha, 0.0F);
         boolean streaks = style == 1;

         for (int i = 0; i < this.trail.length; i++) {
            float t = this.trail[i];
            if (t > 0.02F) {
               float wet = Math.min(1.0F, t);
               this.alpha[i] = streaks ? 0.22F + 0.4F * wet : 0.12F + 0.24F * wet;
               this.color[i] = RainSurface.BODY;
            }
         }

         for (RainSurface.Drop drop : this.drops) {
            int size = drop.size;
            if (drop.dryTicks >= 0) {
               size = Math.max(1, (int)Math.ceil(size * Math.min(1.0F, drop.dryTicks / (drop.dryTotal * 0.6F))));
            }

            if (streaks) {
               int x = Math.round(drop.x - (size - 1) / 2.0F);
               int y = (int)Math.floor(drop.y);

               for (int dx = 0; dx < size; dx++) {
                  this.put(x + dx, y, dx == 0 ? RainSurface.LIGHT : RainSurface.BODY, 0.8F);
                  this.put(x + dx, y + 1, RainSurface.DARK, 0.6F);
               }
            } else if (drop.running) {
               this.drawRunner(drop);
            } else if (size == 1) {
               this.put(Math.round(drop.x), (int)Math.floor(drop.y), drop.bright ? RainSurface.LIGHT : RainSurface.BODY, drop.bright ? 0.85F : 0.65F);
            } else {
               size = Math.min(size, RainSurface.SHAPES.length - 1);
               int left = Math.round(drop.x - (size - 1) / 2.0F);
               int top = Math.round(drop.y - (size - 1) / 2.0F);

               for (int[] cell : size == 2 && this.px <= 16 ? RainSurface.SMALL_16 : RainSurface.SHAPES[size]) {
                  switch (cell[2]) {
                     case 1:
                        this.put(left + cell[0], top + cell[1], RainSurface.LIGHT, 0.95F);
                        break;
                     case 2:
                        this.put(left + cell[0], top + cell[1], RainSurface.DARK, 0.9F);
                        break;
                     default:
                        this.put(left + cell[0], top + cell[1], RainSurface.BODY, 0.6F);
                  }
               }
            }
         }

         for (RainSurface.Trickle t : this.trickles) {
            int y = (int)Math.floor(t.y);

            for (int dx = 0; dx < t.width; dx++) {
               this.put(t.x + dx, y, dx == 0 ? RainSurface.LIGHT : RainSurface.BODY, 0.9F);
               this.put(t.x + dx, y + 1, RainSurface.DARK, 0.75F);
            }
         }

         boolean any = false;

         for (int ix = 0; ix < this.alpha.length; ix++) {
            float a = this.alpha[ix];
            int c = a > 0.0F ? this.color[ix] : RainSurface.BODY;
            int o = ix * 4;
            this.rgba[o] = (byte)(c >> 16);
            this.rgba[o + 1] = (byte)(c >> 8);
            this.rgba[o + 2] = (byte)c;
            this.rgba[o + 3] = (byte)Math.round(a * 255.0F);
            any |= a > 0.0F;
         }

         for (int s = 0; s < 4; s++) {
            byte[] strip = RainSurface.this.stripPixels[this.first + s];

            for (int y = 0; y < this.h; y++) {
               System.arraycopy(this.rgba, (y * this.w + s * this.px) * 4, strip, y * this.px * 4, this.px * 4);
            }
         }

         return any;
      }
   }

   private static final class Trickle {
      int x;
      float y;
      int width;
      float speed;
      float left;
      float nextStep;
   }
}
