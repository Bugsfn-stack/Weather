package com.duffopuffo.weatherglass.fabric;

import com.duffopuffo.weatherglass.WeatherGlass;
import com.duffopuffo.weatherglass.WeatherGlassConfig;
import com.duffopuffo.weatherglass.client.FilmSprites;
import com.duffopuffo.weatherglass.client.RainExposure;
import java.util.function.Consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.MinecraftClient;

public final class WeatherGlassFabric implements ClientModInitializer {
   public void onInitializeClient() {
      WeatherGlassConfig.get();
      RainExposure.meshBiomes = (view, pos) -> ((net.fabricmc.fabric.api.blockview.v2.FabricBlockView) view).hasBiomes() ? ((net.fabricmc.fabric.api.blockview.v2.FabricBlockView) view).getBiomeFabric(pos) : null;
      ModelLoadingPlugin.register(new GlassModels());
      ClientTickEvents.END_CLIENT_TICK.register(FilmSprites::tick);
      Consumer<MinecraftClient> autoTest = WeatherGlass.autoTest();
      if (autoTest != null) {
         ClientTickEvents.END_CLIENT_TICK.register(autoTest::accept);
      }
   }
}
