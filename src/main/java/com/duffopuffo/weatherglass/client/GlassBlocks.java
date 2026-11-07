package com.duffopuffo.weatherglass.client;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.util.DyeColor;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

public final class GlassBlocks {
   private static Set<Block> blocks;

   private GlassBlocks() {
   }

   public static Set<Block> all() {
      if (blocks == null) {
         blocks = Stream.concat(
               Stream.of(Blocks.GLASS, Blocks.TINTED_GLASS, Blocks.GLASS_PANE),
               Arrays.stream(DyeColor.values()).flatMap(color -> Stream.of(stained(color, "_stained_glass"), stained(color, "_stained_glass_pane")))
            )
            .collect(Collectors.toUnmodifiableSet());
      }

      return blocks;
   }

   private static Block stained(DyeColor color, String suffix) {
      return (Block)Registries.BLOCK.get(Identifier.ofVanilla(color.getId() + suffix));
   }

   public static boolean is(BlockState state) {
      return all().contains(state.getBlock());
   }

   public static boolean isPane(BlockState state) {
      return state.getBlock() instanceof PaneBlock;
   }
}
