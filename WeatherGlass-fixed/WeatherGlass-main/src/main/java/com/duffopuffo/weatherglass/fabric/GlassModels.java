package com.duffopuffo.weatherglass.fabric;

import com.duffopuffo.weatherglass.client.FilmLayout;
import com.duffopuffo.weatherglass.client.FilmSprites;
import com.duffopuffo.weatherglass.client.GlassBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;

public final class GlassModels implements ModelLoadingPlugin {
   private SpriteAtlasTexture atlasOfFilms;
   private FilmLayout.Films films;

   @Override
   public void initialize(ModelLoadingPlugin.Context context) {
      context.modifyBlockModelAfterBake().register(ModelModifier.WRAP_PHASE, this::wrap);
   }

   private BlockStateModel wrap(BlockStateModel model, ModelModifier.AfterBakeBlock.Context context) {
      BlockState state = context.state();
      return GlassBlocks.is(state) ? new WeatherGlassModel(model, this::films) : model;
   }

   // Film sprites are looked up in the block atlas the first time a glass block is meshed after each resource reload
   private synchronized FilmLayout.Films films() {
      SpriteAtlasTexture atlas = MinecraftClient.getInstance().getAtlasManager().getAtlasTexture(Identifier.ofVanilla("blocks"));
      if (atlas != this.atlasOfFilms) {
         this.films = new FilmLayout.Films(
            get(atlas, 64, FilmSprites::rainId),
            get(atlas, 32, FilmSprites::snowId),
            get(atlas, 16, FilmSprites::frostId),
            get(atlas, 16, FilmSprites::insideFrostId),
            get(atlas, 8, FilmSprites::breathId),
            get(atlas, 64, (pixels, i) -> FilmSprites.dustId(pixels, false, i)),
            get(atlas, 64, (pixels, i) -> FilmSprites.dustId(pixels, true, i))
         );
         this.atlasOfFilms = atlas;
      }

      return this.films;
   }

   private static Sprite[][] get(SpriteAtlasTexture atlas, int count, Ids ids) {
      Sprite[][] got = new Sprite[FilmSprites.PIXELS.length][count];

      for (int res = 0; res < got.length; res++) {
         for (int i = 0; i < count; i++) {
            got[res][i] = atlas.getSprite(ids.id(FilmSprites.PIXELS[res], i));
         }
      }

      return got;
   }

   @FunctionalInterface
   private interface Ids {
      Identifier id(int pixels, int index);
   }
}
