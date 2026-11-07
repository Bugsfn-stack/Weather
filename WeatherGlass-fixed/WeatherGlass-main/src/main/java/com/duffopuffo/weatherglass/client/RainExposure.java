package com.duffopuffo.weatherglass.client;

import net.minecraft.world.BlockRenderView;
import net.minecraft.world.biome.Biome;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.world.biome.Biome.Precipitation;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.Heightmap.Type;

public final class RainExposure {
   public static final int REACH = 3;
   public static final int OPEN = 1;
   public static final int REACHED = 2;
   public static final int COLD = 4;
   public static final int RAINY = 8;
   public static final int DUSTY = 16;
   public static final int RED = 32;
   public static RainExposure.MeshBiomes meshBiomes = (view, pos) -> null;
   public static final int SKY_SEARCH = 8;

   private RainExposure() {
   }

   public static int check(BlockRenderView view, BlockPos pos, Direction side) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level == null) {
         return 0;
      } else {
         BlockPos front = pos.offset(side);
         BlockState state = view.getBlockState(front);
         if (!GlassBlocks.is(state) && !blocksRain(state)) {
            int flags = 1;
            RegistryEntry<Biome> biome = meshBiomes.at(view, front);
            if (biome == null) {
               biome = level.getBiome(front);
            }

            Precipitation precipitation = ((Biome)biome.value()).getPrecipitation(front, level.getSeaLevel());
            if (precipitation == Precipitation.SNOW) {
               flags |= 4;
            } else if (precipitation == Precipitation.RAIN) {
               flags |= 8;
            } else if (biome.isIn(BiomeTags.IS_BADLANDS)) {
               flags |= 48;
            } else {
               if (!level.getDimension().hasSkyLight() || level.getDimension().hasCeiling()) {
                  return flags;
               }

               flags |= 16;
            }

            Mutable cell = front.mutableCopy();

            for (int d = 1; d <= 3; d++) {
               if (d > 1) {
                  cell.move(side);
                  if (blocksRain(view.getBlockState(cell))) {
                     return flags;
                  }
               }

               if (cell.getY() >= level.getTopY(Type.MOTION_BLOCKING, cell.getX(), cell.getZ())) {
                  return flags | 2;
               }
            }

            return flags;
         } else {
            return 0;
         }
      }
   }

   public static boolean outside(BlockRenderView view, BlockPos pos, Direction side) {
      int here = skyDistance(view, pos, side);
      return here <= 8 && here <= skyDistance(view, pos, side.getOpposite());
   }

   private static int skyDistance(BlockRenderView view, BlockPos pos, Direction side) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level == null) {
         return 9;
      } else {
         Mutable cell = pos.mutableCopy();

         for (int d = 1; d <= 8; d++) {
            cell.move(side);
            BlockState state = view.getBlockState(cell);
            if (blocksRain(state) || GlassBlocks.is(state)) {
               return 9;
            }

            if (cell.getY() >= level.getTopY(Type.MOTION_BLOCKING, cell.getX(), cell.getZ())) {
               return d;
            }
         }

         return 9;
      }
   }

   public static int windowEdges(BlockRenderView view, BlockPos pos, Direction side) {
      int mask = 0;
      if (!GlassBlocks.is(view.getBlockState(pos.up()))) {
         mask |= 1;
      }

      if (!GlassBlocks.is(view.getBlockState(pos.down()))) {
         mask |= 2;
      }

      if (!GlassBlocks.is(view.getBlockState(pos.offset(side.rotateYClockwise())))) {
         mask |= 4;
      }

      if (!GlassBlocks.is(view.getBlockState(pos.offset(side.rotateYCounterclockwise())))) {
         mask |= 8;
      }

      return mask;
   }

   private static boolean blocksRain(BlockState state) {
      return Type.MOTION_BLOCKING.getBlockPredicate().test(state);
   }

   @FunctionalInterface
   public interface MeshBiomes {
      RegistryEntry<Biome> at(BlockRenderView var1, BlockPos var2);
   }
}
