package com.duffopuffo.weatherglass;

import com.duffopuffo.weatherglass.client.GlassPreview;
import com.duffopuffo.weatherglass.client.WindSource;
import java.util.function.Consumer;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.CyclingButtonWidget.Builder;

public class WeatherGlassConfigScreen extends Screen {
   private static final int GAP = 12;
   private final Screen parent;
   private final int[] old;
   private final boolean[] oldFlags;
   private GlassPreview rainPreview;
   private GlassPreview snowPreview;
   private GlassPreview dustPreview;
   private int previewSize;
   private int previewY;
   private int leftX;
   private int middleX;
   private int rightX;
   private int column;

   public WeatherGlassConfigScreen(Screen parent) {
      super(Text.translatable("weatherglass.config.title"));
      this.parent = parent;
      WeatherGlassConfig config = WeatherGlassConfig.get();
      this.old = new int[]{config.rainAmount, config.rainStyle, config.rainPixels, config.snowAmount, config.snowPixels, config.dustAmount, config.dustPixels};
      this.oldFlags = new boolean[]{config.insideFrost, config.breath, config.wind};
   }

   protected void init() {
      WeatherGlassConfig config = WeatherGlassConfig.get();
      this.column = Math.min(170, (this.width - 48) / 3);
      this.middleX = this.width / 2 - this.column / 2;
      this.leftX = this.middleX - 12 - this.column;
      this.rightX = this.middleX + this.column + 12;
      int bottom = this.height - 28;
      boolean wind = !WindSource.found().isEmpty();
      int rows = wind ? 5 : 4;
      int buttonsY = bottom - 8 - rows * 24;
      this.previewY = 24;
      this.previewSize = Math.max(40, Math.min(Math.min(128, this.column), buttonsY - this.previewY - 18));
      if (this.rainPreview == null) {
         this.rainPreview = new GlassPreview(GlassPreview.Kind.RAIN);
         this.snowPreview = new GlassPreview(GlassPreview.Kind.SNOW);
         this.dustPreview = new GlassPreview(GlassPreview.Kind.DUST);
      }

      this.addDrawableChild(this.amount("weatherglass.config.rain", config.rainAmount, this.leftX, buttonsY, value -> apply(c -> c.rainAmount = value)));
      this.addDrawableChild(this.amount("weatherglass.config.snow", config.snowAmount, this.middleX, buttonsY, value -> apply(c -> c.snowAmount = value)));
      this.addDrawableChild(this.amount("weatherglass.config.dust", config.dustAmount, this.rightX, buttonsY, value -> apply(c -> c.dustAmount = value)));
      this.addDrawableChild(this.pixels("weatherglass.config.rain_pixels", config.rainPixels, this.leftX, buttonsY + 24, value -> apply(c -> c.rainPixels = value)));
      this.addDrawableChild(
         this.pixels("weatherglass.config.snow_pixels", config.snowPixels, this.middleX, buttonsY + 24, value -> apply(c -> c.snowPixels = value))
      );
      this.addDrawableChild(
         this.pixels("weatherglass.config.dust_pixels", config.dustPixels, this.rightX, buttonsY + 24, value -> apply(c -> c.dustPixels = value))
      );
      ((CyclingButtonWidget)this.addDrawableChild(
            CyclingButtonWidget.builder(value -> Text.translatable("weatherglass.config.style." + value), config.rainStyle)
               .values(new Integer[]{0, 1})
               .build(
                  this.leftX, buttonsY + 48, this.column, 20, Text.translatable("weatherglass.config.style"), (button, value) -> config.rainStyle = value
               )
         ))
         .setTooltip(Tooltip.of(Text.translatable("weatherglass.config.style.tooltip")));
      ((CyclingButtonWidget)this.addDrawableChild(
            onOff(config.insideFrost)
               .build(
                  this.middleX,
                  buttonsY + 48,
                  this.column,
                  20,
                  Text.translatable("weatherglass.config.inside_frost"),
                  (button, value) -> apply(c -> c.insideFrost = value)
               )
         ))
         .setTooltip(Tooltip.of(Text.translatable("weatherglass.config.inside_frost.tooltip")));
      ((CyclingButtonWidget)this.addDrawableChild(
            onOff(config.breath)
               .build(
                  this.middleX, buttonsY + 72, this.column, 20, Text.translatable("weatherglass.config.breath"), (button, value) -> config.breath = value
               )
         ))
         .setTooltip(Tooltip.of(Text.translatable("weatherglass.config.breath.tooltip")));
      if (wind) {
         String mods = String.join(" & ", WindSource.found());
         int wide = 2 * this.column + 12;
         ((CyclingButtonWidget)this.addDrawableChild(
               onOff(config.wind)
                  .build(
                     this.width / 2 - wide / 2,
                     buttonsY + 96,
                     wide,
                     20,
                     Text.translatable("weatherglass.config.wind", new Object[]{mods})
                        .append(Text.literal(" BETA").formatted(Formatting.GOLD)),
                     (button, value) -> config.wind = value
                  )
            ))
            .setTooltip(Tooltip.of(Text.translatable("weatherglass.config.wind.tooltip", new Object[]{mods})));
      }

      int half = Math.min(150, this.column);
      this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> {
         config.clampAll();
         config.save();
         this.close();
      }).dimensions(this.width / 2 - 6 - half, bottom, half, 20).build());
      this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> {
         apply(c -> {
            c.rainAmount = this.old[0];
            c.rainStyle = this.old[1];
            c.rainPixels = this.old[2];
            c.snowAmount = this.old[3];
            c.snowPixels = this.old[4];
            c.dustAmount = this.old[5];
            c.dustPixels = this.old[6];
            c.insideFrost = this.oldFlags[0];
         });
         config.breath = this.oldFlags[1];
         config.wind = this.oldFlags[2];
         this.close();
      }).dimensions(this.width / 2 + 6, bottom, half, 20).build());
   }

   private static Builder<Boolean> onOff(boolean value) {
      return CyclingButtonWidget.onOffBuilder(ScreenTexts.ON.copy().formatted(Formatting.GREEN), ScreenTexts.OFF, value);
   }

   private CyclingButtonWidget<Integer> amount(String key, int value, int x, int y, Consumer<Integer> set) {
      CyclingButtonWidget<Integer> button = CyclingButtonWidget.builder(v -> Text.translatable("weatherglass.config.amount." + v), value)
         .values(new Integer[]{0, 1, 2, 3})
         .build(x, y, this.column, 20, Text.translatable(key), (b, v) -> set.accept(v));
      button.setTooltip(Tooltip.of(Text.translatable(key + ".tooltip")));
      return button;
   }

   private CyclingButtonWidget<Integer> pixels(String key, int value, int x, int y, Consumer<Integer> set) {
      CyclingButtonWidget<Integer> button = CyclingButtonWidget.builder(v -> Text.translatable("weatherglass.config.pixels." + v), value)
         .values(new Integer[]{16, 32, 64})
         .build(x, y, this.column, 20, Text.translatable("weatherglass.config.pixels"), (b, v) -> set.accept(v));
      button.setTooltip(Tooltip.of(Text.translatable(key + ".tooltip")));
      return button;
   }

   private static void apply(Consumer<WeatherGlassConfig> change) {
      WeatherGlassConfig config = WeatherGlassConfig.get();
      String before = meshKey(config);
      change.accept(config);
      if (!before.equals(meshKey(config)) && MinecraftClient.getInstance().world != null) {
         MinecraftClient.getInstance().worldRenderer.reload();
      }
   }

   private static String meshKey(WeatherGlassConfig c) {
      return c.rainOn() + "/" + c.snowOn() + "/" + c.dustOn() + "/" + c.rainPixels + "/" + c.snowPixels + "/" + c.dustPixels + "/" + c.insideFrost;
   }

   public void tick() {
      super.tick();
      WeatherGlassConfig config = WeatherGlassConfig.get();
      if (this.rainPreview != null && this.snowPreview != null && this.dustPreview != null) {
         this.rainPreview.tick(config);
         this.snowPreview.tick(config);
         this.dustPreview.tick(config);
      }
   }

   public void render(DrawContext graphics, int mouseX, int mouseY, float a) {
      super.render(graphics, mouseX, mouseY, a);
      graphics.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 9, -1);
      if (this.rainPreview != null && this.snowPreview != null && this.dustPreview != null) {
         int size = this.previewSize;
         int rainX = this.leftX + (this.column - size) / 2;
         int snowX = this.middleX + (this.column - size) / 2;
         int dustX = this.rightX + (this.column - size) / 2;
         this.rainPreview.draw(graphics, rainX, this.previewY, size);
         this.snowPreview.draw(graphics, snowX, this.previewY, size);
         this.dustPreview.draw(graphics, dustX, this.previewY, size);
         int captionY = this.previewY + size + 5;
         graphics.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("weatherglass.config.rain_title"), rainX + size / 2, captionY, -2300161);
         graphics.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("weatherglass.config.snow_title"), snowX + size / 2, captionY, -2300161);
         graphics.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.translatable("weatherglass.config.dust_title").append(Text.literal(" BETA").formatted(Formatting.GOLD)),
            dustX + size / 2,
            captionY,
            -2300161
         );
      }
   }

   public void removed() {
      if (this.rainPreview != null) {
         this.rainPreview.close();
         this.snowPreview.close();
         this.dustPreview.close();
         this.rainPreview = null;
         this.snowPreview = null;
         this.dustPreview = null;
      }

      super.removed();
   }

   public void close() {
      this.client.setScreen(this.parent);
   }
}
