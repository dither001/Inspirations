package knightminer.inspirations.recipes.recipe.inventory;

import knightminer.inspirations.Inspirations;
import knightminer.inspirations.library.recipe.RecipeTypes;
import knightminer.inspirations.library.recipe.cauldron.CauldronContentTypes;
import knightminer.inspirations.library.recipe.cauldron.contents.EmptyCauldronContents;
import knightminer.inspirations.library.recipe.cauldron.contents.ICauldronContents;
import knightminer.inspirations.library.recipe.cauldron.recipe.ICauldronRecipe;
import knightminer.inspirations.recipes.block.EnhancedCauldronBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CauldronBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.function.Consumer;

public class VanillaCauldronInventory extends CauldronItemInventory {
  private static final ICauldronContents CONTENTS = CauldronContentTypes.FLUID.of(Fluids.WATER);
  private final World world;
  private final BlockPos pos;
  private final BlockState state;

  // cached boiling value
  private Boolean boiling;

  /**
   * Main constructor with all parameters
   * @param world        World containing the cauldron
   * @param pos         Position of the cauldron
   * @param state       Cauldron block state
   * @param stack       Item stack used to interact
   * @param itemSetter  Logic to update the item stack in context
   * @param itemAdder   Logic to give the context a new item stack
   */
  public VanillaCauldronInventory(World world, BlockPos pos, BlockState state, ItemStack stack, Consumer<ItemStack> itemSetter, Consumer<ItemStack> itemAdder) {
    super(stack, itemSetter, itemAdder);
    this.state = state;
    this.world = world;
    this.pos = pos;
  }

  /**
   * Constructor for contexts which directly handle the item stack. Will use {@link #getStack()} to update stacks
   * @param world       World containing the cauldron
   * @param pos         Position of the cauldron
   * @param state       Cauldron block state
   * @param stack       Item stack used to interact
   * @param itemAdder   Logic to give the context a new item stack
   */
  public VanillaCauldronInventory(World world, BlockPos pos, BlockState state, ItemStack stack, Consumer<ItemStack> itemAdder) {
    this(world, pos, state, stack, EMPTY_CONSUMER, itemAdder);
  }

  @Override
  public boolean isSimple() {
    return true;
  }

  @Override
  public void playSound(SoundEvent sound) {
    world.playSound(null, pos, sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
  }

  /* Levels */

  @Override
  public int getLevel() {
    return state.get(CauldronBlock.LEVEL);
  }

  @Override
  public void setLevel(int level) {
    level = MathHelper.clamp(level, 0, ICauldronRecipe.MAX);
    if (state.get(CauldronBlock.LEVEL) != level) {
      ((CauldronBlock) Blocks.CAULDRON).setWaterLevel(world, pos, state, level);
    }
  }


  /* Contents */

  @Override
  public ICauldronContents getContents() {
    if (getLevel() == 0) {
      return EmptyCauldronContents.INSTANCE;
    }
    return CONTENTS;
  }

  @Override
  public void setContents(ICauldronContents contents) {
    if (contents != EmptyCauldronContents.INSTANCE && !contents.isSimple()) {
      Inspirations.log.error("Cannot set cauldron contents of vanilla cauldron to non-water " + contents);
    }
  }

  @Override
  public boolean isBoiling() {
    if (boiling == null) {
      boiling = EnhancedCauldronBlock.isCauldronFire(world.getBlockState(pos.down()));
    }
    return boiling;
  }


  /* Logic to run the recipe */

  /**
   * Handles the recipe using this object as the context
   * @return  True if a recipe happened, false otherwise
   */
  public boolean handleRecipe() {
    Optional<ICauldronRecipe> recipe = world.getRecipeManager().getRecipe(RecipeTypes.CAULDRON, this, world);
    if (recipe.isPresent()) {
      recipe.get().handleRecipe(this);
      return true;
    }
    return false;
  }
}
