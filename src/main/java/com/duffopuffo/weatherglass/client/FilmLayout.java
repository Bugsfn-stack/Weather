package com.duffopuffo.weatherglass.client;

import com.duffopuffo.weatherglass.WeatherGlassConfig;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.texture.Sprite;
import net.minecraft.world.BlockRenderView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Direction.AxisDirection;
import net.minecraft.util.math.Direction.Type;

public final class FilmLayout {
   private static final float GAP = 0.0025F;
   private static final float BREATH_GAP = 0.0035F;
   private static final float SNOW_GAP = 0.005F;
   private static final float PANE_NEAR = 0.4375F;
   private static final float PANE_FAR = 0.5625F;
   public static final AtomicInteger FILMS = new AtomicInteger();
   public static final AtomicInteger SNOW_FILMS = new AtomicInteger();
   public static final AtomicInteger FROST_FILMS = new AtomicInteger();
   public static final AtomicInteger INSIDE_FROST_FILMS = new AtomicInteger();
   public static final AtomicInteger BREATH_FILMS = new AtomicInteger();
   public static final AtomicInteger DUST_FILMS = new AtomicInteger();

   private FilmLayout() {
   }

   public static void collect(
      BlockRenderView view, BlockPos pos, BlockState state, boolean pane, Predicate<Direction> culled, FilmLayout.Films films, FilmLayout.FilmSink sink
   ) {
      WeatherGlassConfig config = WeatherGlassConfig.get();
      if (config.enabled()) {
         for (Direction side : Type.HORIZONTAL) {
            float from = 0.0F;
            float to = 1.0F;
            float plane;
            if (pane) {
               if ((Boolean)state.get((Property)ConnectingBlock.FACING_PROPERTIES.get(side))) {
                  continue;
               }

               Direction negative = side.getAxis() == Axis.Z ? Direction.WEST : Direction.NORTH;
               boolean low = (Boolean)state.get((Property)ConnectingBlock.FACING_PROPERTIES.get(negative));
               boolean high = (Boolean)state.get((Property)ConnectingBlock.FACING_PROPERTIES.get(negative.getOpposite()));
               if (!low && !high) {
                  continue;
               }

               plane = side.getDirection() == AxisDirection.POSITIVE ? 0.5625F : 0.4375F;
               from = low ? 0.0F : 0.4375F;
               to = high ? 1.0F : 0.5625F;
            } else {
               if (culled.test(side)) {
                  continue;
               }

               plane = side.getDirection() == AxisDirection.POSITIVE ? 1.0F : 0.0F;
            }

            int weather = RainExposure.check(view, pos, side);
            if ((weather & 1) != 0) {
               int rainRes = FilmSprites.res(config.rainPixels);
               int res = FilmSprites.res(config.snowPixels);
               int strip = FilmSprites.rainIndex(pos.getX(), pos.getY(), pos.getZ(), side);
               int flakes = FilmSprites.snowIndex(pos.getX(), pos.getY(), pos.getZ(), side);
               if ((weather & 8) != 0 && (weather & 2) != 0) {
                  if (config.rainOn()) {
                     FilmSprites.moving(pos);
                     FilmSprites.rainFilms = true;
                     FilmSprites.exposed(0, pos);
                     if (FilmSprites.shown(0)) {
                        FILMS.incrementAndGet();
                        sink.film(side, plane, from, to, films.rain()[rainRes][strip], 0.0025F);
                     }
                  }
               } else if ((weather & 4) != 0 && config.snowOn()) {
                  int edges = RainExposure.windowEdges(view, pos, side);
                  if (RainExposure.outside(view, pos, side)) {
                     FROST_FILMS.incrementAndGet();
                     FilmSprites.frostFilms = true;
                     sink.film(side, plane, from, to, films.frost()[res][edges], 0.0025F);
                  } else if (config.insideFrost && RainExposure.outside(view, pos, side.getOpposite())) {
                     INSIDE_FROST_FILMS.incrementAndGet();
                     FilmSprites.frostFilms = true;
                     sink.film(side, plane, from, to, films.insideFrost()[res][edges], 0.0025F);
                  }

                  if ((weather & 2) != 0) {
                     FilmSprites.moving(pos);
                     FilmSprites.snowFilms = true;
                     FilmSprites.exposed(1, pos);
                     if (FilmSprites.shown(1)) {
                        SNOW_FILMS.incrementAndGet();
                        sink.film(side, plane, from, to, films.snow()[res][flakes], 0.005F);
                     }
                  }

                  int breath = BreathFog.slot(pos, side);
                  if (breath >= 0 && config.breath) {
                     BREATH_FILMS.incrementAndGet();
                     sink.film(side, plane, from, to, films.breath()[res][breath], 0.0035F);
                  }
               } else if ((weather & 16) != 0 && (weather & 2) != 0 && config.dustOn()) {
                  boolean red = (weather & 32) != 0;
                  int edgesx = RainExposure.windowEdges(view, pos, side);
                  if (red) {
                     FilmSprites.redDustFilms = true;
                  } else {
                     FilmSprites.dustFilms = true;
                  }

                  FilmSprites.exposed(2, pos);
                  if (FilmSprites.shown(2)) {
                     DUST_FILMS.incrementAndGet();
                     Sprite[] dust = (red ? films.redDust() : films.dust())[FilmSprites.res(config.dustPixels)];
                     sink.film(side, plane, from, to, dust[side.getHorizontalQuarterTurns() * 16 + edgesx], 0.0025F);
                  }
               }
            }
         }
      }
   }

   public static void quads(Direction side, float plane, float from, float to, float gap, boolean pane, FilmLayout.QuadSink sink) {
      float depth = plane + (side.getDirection() == AxisDirection.POSITIVE ? gap : -gap);
      float u0;
      float u1;
      switch (side) {
         case NORTH:
         case EAST:
            u0 = 1.0F - to;
            u1 = 1.0F - from;
            break;
         default:
            u0 = from;
            u1 = to;
      }

      float[] vertices = new float[20];
      vertex(vertices, 0, side, depth, u0, 1.0F);
      vertex(vertices, 1, side, depth, u0, 0.0F);
      vertex(vertices, 2, side, depth, u1, 0.0F);
      vertex(vertices, 3, side, depth, u1, 1.0F);
      sink.quad(vertices, side, pane ? null : side);
      vertex(vertices, 0, side, depth, u1, 1.0F);
      vertex(vertices, 1, side, depth, u1, 0.0F);
      vertex(vertices, 2, side, depth, u0, 0.0F);
      vertex(vertices, 3, side, depth, u0, 1.0F);
      sink.quad(vertices, side.getOpposite(), null);
   }

   private static void vertex(float[] vertices, int index, Direction side, float depth, float u, float y) {
      float x;
      float z;
      switch (side) {
         case NORTH:
            x = 1.0F - u;
            z = depth;
            break;
         case EAST:
            x = depth;
            z = 1.0F - u;
            break;
         case SOUTH:
            x = u;
            z = depth;
            break;
         default:
            x = depth;
            z = u;
      }

      int at = index * 5;
      vertices[at] = x;
      vertices[at + 1] = y;
      vertices[at + 2] = z;
      vertices[at + 3] = u;
      vertices[at + 4] = 1.0F - y;
   }

   @FunctionalInterface
   public interface FilmSink {
      void film(Direction var1, float var2, float var3, float var4, Sprite var5, float var6);
   }

   public record Films(
      Sprite[][] rain,
      Sprite[][] snow,
      Sprite[][] frost,
      Sprite[][] insideFrost,
      Sprite[][] breath,
      Sprite[][] dust,
      Sprite[][] redDust
   ) {
      public Sprite[][] all() {
         return Stream.of(this.rain, this.snow, this.frost, this.insideFrost, this.breath, this.dust, this.redDust)
            .flatMap(Arrays::stream)
            .toArray(Sprite[][]::new);
      }
   }

   @FunctionalInterface
   public interface QuadSink {
      void quad(float[] var1, Direction var2, Direction var3);
   }
}
