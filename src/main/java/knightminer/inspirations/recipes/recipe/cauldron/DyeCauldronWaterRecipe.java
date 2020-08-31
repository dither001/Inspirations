package knightminer.inspirations.recipes.recipe.cauldron;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import knightminer.inspirations.library.recipe.RecipeSerializer;
import knightminer.inspirations.library.recipe.cauldron.CauldronContentTypes;
import knightminer.inspirations.library.recipe.cauldron.contents.ICauldronContents;
import knightminer.inspirations.library.recipe.cauldron.inventory.ICauldronInventory;
import knightminer.inspirations.library.recipe.cauldron.inventory.IModifyableCauldronInventory;
import knightminer.inspirations.library.recipe.cauldron.recipe.ICauldronRecipe;
import knightminer.inspirations.recipes.InspirationsRecipes;
import net.minecraft.data.IFinishedRecipe;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvents;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Recipe that takes a solid dye and mixes it with liquid in the cauldron
 */
public class DyeCauldronWaterRecipe implements ICauldronRecipe {
  private final ResourceLocation id;
  private final DyeColor dye;
  public DyeCauldronWaterRecipe(ResourceLocation id, DyeColor dye) {
    this.id = id;
    this.dye = dye;
  }

  @Override
  public boolean matches(ICauldronInventory inv, World worldIn) {
    // must have at least 1 level, and must match dye tag
    // nothing with a container, that behaves differently
    ItemStack stack = inv.getStack();
    if (inv.getLevel() == 0 || !dye.getTag().contains(stack.getItem()) || !stack.getContainerItem().isEmpty()) {
      return false;
    }

    // can dye water and other dyes
    // cannot dye if already that color
    ICauldronContents contents = inv.getContents();
    return contents.contains(CauldronContentTypes.FLUID, Fluids.WATER)
           || contents.get(CauldronContentTypes.COLOR)
                      .filter(color -> color != dye.getColorValue())
                      .isPresent();
  }

  @Override
  public void handleRecipe(IModifyableCauldronInventory inv) {
    ICauldronContents contents = inv.getContents();
    // if water, set the color directly
    if (contents.contains(CauldronContentTypes.FLUID, Fluids.WATER)) {
      // update dye stack
      inv.shrinkStack(1);

      // update contents
      inv.setContents(CauldronContentTypes.DYE.of(dye));

      // play sound
      inv.playSound(SoundEvents.ENTITY_FISHING_BOBBER_SPLASH);
    } else {
      contents.get(CauldronContentTypes.COLOR).ifPresent(color -> {
        // update dye stack
        inv.shrinkStack(1);

        // update contents
        inv.setContents(CauldronContentTypes.COLOR.of(addColors(dye.getColorValue(), color)));

        // play sound
        inv.playSound(SoundEvents.ENTITY_GENERIC_SPLASH);
      });
    }
  }

  /**
   * Adds two colors
   * @param base    First color
   * @param colors  Additional colors
   * @return  Added colors
   */
  public static int addColors(int base, int... colors) {
    // sum color components
    int r = getRed(base);
    int g = getGreen(base);
    int b = getBlue(base);
    // base acts as preference
    int pr = r, pg = g, pb = b;
    for (int color : colors) {
      r += getRed(color);
      g += getGreen(color);
      b += getBlue(color);
    }
    // divide per component, tending towards base
    int c = colors.length + 1;
    return divide(r, pr, c) << 16 | divide(g, pg, c) << 8 | divide(b, pb, c);
  }

  /**
   * Divides a sum of colors, favoring pref if the remainder is non-zero
   * @param sum      Color sum
   * @param pref     Preferred component
   * @param divisor  Number to divide by
   * @return Divided sum
   */
  public static int divide(int sum, int pref, int divisor) {
    int color = sum / divisor;
    // if there was a remainder, favor the original color
    if (sum % divisor != 0 && pref > color) {
      color++;
    }
    return color;
  }

  /**
   * Gets the red value of a color
   * @param color  Color
   * @return Red value
   */
  private static int getRed(int color) {
    return (color & 0xFF0000) >> 16;
  }

  /**
   * Gets the green value of a color
   * @param color  Color
   * @return Green value
   */
  private static int getGreen(int color) {
    return (color & 0xFF00) >> 8;
  }

  /**
   * Gets the blue value of a color
   * @param color  Color
   * @return Blue value
   */
  private static int getBlue(int color) {
    return (color & 0xFF);
  }

  @Override
  public ItemStack getRecipeOutput() {
    return ItemStack.EMPTY;
  }

  @Override
  public ResourceLocation getId() {
    return id;
  }

  @Override
  public IRecipeSerializer<?> getSerializer() {
    return InspirationsRecipes.dyeCauldronWaterSerializer;
  }

  public static class Serializer extends RecipeSerializer<DyeCauldronWaterRecipe> {
    @SuppressWarnings("ConstantConditions")
    @Override
    public DyeCauldronWaterRecipe read(ResourceLocation id, JsonObject json) {
      String name = JSONUtils.getString(json, "dye");
      DyeColor dye = DyeColor.byTranslationKey(name, null);
      if (dye == null) {
        throw new JsonSyntaxException("Invalid color " + name);
      }
      return new DyeCauldronWaterRecipe(id, dye);
    }

    @Nullable
    @Override
    public DyeCauldronWaterRecipe read(ResourceLocation id, PacketBuffer buffer) {
      return new DyeCauldronWaterRecipe(id, buffer.readEnumValue(DyeColor.class));
    }

    @Override
    public void write(PacketBuffer buffer, DyeCauldronWaterRecipe recipe) {
      buffer.writeEnumValue(recipe.dye);
    }
  }

  /**
   * Finished recipe class for datagen
   */
  public static class FinishedRecipe implements IFinishedRecipe {
    private final ResourceLocation id;
    private final DyeColor dye;
    public FinishedRecipe(ResourceLocation id, DyeColor dye) {
      this.id = id;
      this.dye = dye;
    }

    @Override
    public void serialize(JsonObject json) {
      json.addProperty("dye", dye.getString());
    }

    @Override
    public ResourceLocation getID() {
      return id;
    }

    @Override
    public IRecipeSerializer<?> getSerializer() {
      return InspirationsRecipes.dyeCauldronWaterSerializer;
    }

    @Nullable
    @Override
    public JsonObject getAdvancementJson() {
      return null;
    }

    @Nullable
    @Override
    public ResourceLocation getAdvancementID() {
      return null;
    }
  }
}
