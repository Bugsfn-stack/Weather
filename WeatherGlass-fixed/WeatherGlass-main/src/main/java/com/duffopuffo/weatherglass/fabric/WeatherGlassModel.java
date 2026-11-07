package com.duffopuffo.weatherglass.fabric;

import com.duffopuffo.weatherglass.client.FilmLayout;
import com.duffopuffo.weatherglass.client.GlassBlocks;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

// Wraps a glass block model and adds the weather films (rain, snow, frost, breath, dust) on top of it
public final class WeatherGlassModel extends WrapperBlockStateModel {
   private final Supplier<FilmLayout.Films> films;

   public WeatherGlassModel(BlockStateModel wrapped, Supplier<FilmLayout.Films> films) {
      super(wrapped);
      this.films = films;
   }

   @Override
   public void emitQuads(QuadEmitter emitter, BlockRenderView view, BlockPos pos, BlockState state, Random random, Predicate<Direction> cullTest) {
      super.emitQuads(emitter, view, pos, state, random, cullTest);
      boolean pane = GlassBlocks.isPane(state);
      FilmLayout.collect(
         view,
         pos,
         state,
         pane,
         cullTest,
         this.films.get(),
         (side, plane, from, to, film, gap) -> FilmLayout.quads(side, plane, from, to, gap, pane, (vertices, face, cull) -> {
            for (int i = 0; i < 4; i++) {
               emitter.pos(i, vertices[i * 5], vertices[i * 5 + 1], vertices[i * 5 + 2]);
               emitter.uv(i, vertices[i * 5 + 3], vertices[i * 5 + 4]);
            }

            emitter.spriteBake(film, MutableQuadView.BAKE_NORMALIZED);
            emitter.color(-1, -1, -1, -1);
            emitter.tintIndex(-1);
            emitter.nominalFace(face);
            emitter.cullFace(cull);
            emitter.renderLayer(BlockRenderLayer.TRANSLUCENT);
            emitter.ambientOcclusion(TriState.FALSE);
            emitter.emit();
         })
      );
   }

   // The films depend on the world, so this model must never share cached geometry
   @Override
   public Object createGeometryKey(BlockRenderView view, BlockPos pos, BlockState state, Random random) {
      return null;
   }
}
