package com.duffopuffo.weatherglass;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WeatherGlass {
   public static final String MOD_ID = "weatherglass";
   public static final Logger LOG = LoggerFactory.getLogger("weatherglass");
   private static final String AUTO_TEST = "com.duffopuffo.weatherglass.dev.WeatherGlassAutoTest";

   private WeatherGlass() {
   }

   public static Consumer<MinecraftClient> autoTest() {
      if (!Boolean.getBoolean("weatherglass.autotest")) {
         return null;
      } else {
         try {
            Class<?> test = Class.forName("com.duffopuffo.weatherglass.dev.WeatherGlassAutoTest");
            test.getMethod("start").invoke(null);
            MethodHandle tick = MethodHandles.publicLookup().findStatic(test, "tick", MethodType.methodType(void.class, MinecraftClient.class));
            return mc -> {
               try {
                  tick.invokeExact((MinecraftClient)mc);
               } catch (Throwable var3) {
                  throw new IllegalStateException("Weather Glass self-test failed", var3);
               }
            };
         } catch (ReflectiveOperationException var2) {
            LOG.warn("No Weather Glass self-test here: it is only in development runs", var2);
            return null;
         }
      }
   }
}
