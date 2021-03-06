package knightminer.inspirations.recipes.tileentity;

import knightminer.inspirations.Inspirations;
import knightminer.inspirations.common.Config;
import knightminer.inspirations.common.network.CauldronStateUpdatePacket;
import knightminer.inspirations.common.network.CauldronTransformUpatePacket;
import knightminer.inspirations.common.network.InspirationsNetwork;
import knightminer.inspirations.library.InspirationsTags;
import knightminer.inspirations.library.Util;
import knightminer.inspirations.library.recipe.RecipeTypes;
import knightminer.inspirations.library.recipe.cauldron.CauldronContentTypes;
import knightminer.inspirations.library.recipe.cauldron.contents.ICauldronContents;
import knightminer.inspirations.library.recipe.cauldron.recipe.ICauldronRecipe;
import knightminer.inspirations.library.recipe.cauldron.recipe.ICauldronTransform;
import knightminer.inspirations.library.recipe.cauldron.util.CauldronTemperature;
import knightminer.inspirations.recipes.InspirationsRecipes;
import knightminer.inspirations.recipes.block.EnhancedCauldronBlock;
import knightminer.inspirations.recipes.recipe.inventory.CauldronItemInventory;
import knightminer.inspirations.recipes.recipe.inventory.TileCauldronInventory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Potion;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tags.FluidTags;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraftforge.client.model.data.IModelData;
import net.minecraftforge.client.model.data.ModelDataMap;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.common.util.Constants.NBT;
import slimeknights.mantle.recipe.RecipeHelper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Tile entity logic for the cauldron, handles more complex content types
 */
public class CauldronTileEntity extends TileEntity implements ITickableTileEntity {
  private static final DamageSource DAMAGE_BOIL = new DamageSource(Inspirations.prefix("boiling")).setDamageBypassesArmor();
  public static final ModelProperty<ResourceLocation> TEXTURE = new ModelProperty<>();
  public static final ModelProperty<Boolean> FROSTED = new ModelProperty<>();
  public static final ModelProperty<Integer> OFFSET = new ModelProperty<>();

  // data objects
  private final IModelData data = new ModelDataMap.Builder()
      .withInitial(TEXTURE, CauldronContentTypes.DEFAULT.get().getTextureName())
      .withInitial(FROSTED, false)
      .withInitial(OFFSET, 0).build();
  private final TileCauldronInventory craftingInventory = new TileCauldronInventory(this);

  // capabilities
  /* TODO: have to determine what sort of automation I want, waiting on dispenser stuff
  private final CauldronItemHandler itemHandler = new CauldronItemHandler(this, craftingInventory);
  private final LazyOptional<IItemHandler> itemHandlerCap = LazyOptional.of(() -> itemHandler);
  private final CauldronFluidHandler fluidHandler = new CauldronFluidHandler(this);
  private final LazyOptional<IFluidHandler> fluidHandlerCap = LazyOptional.of(() -> fluidHandler);
   */

  // cauldron properties
  /** Current cauldron contents */
  private ICauldronContents contents;
  /** Offset amount for liquid. Formula is LEVEL * 4 + offset */
  private int levelOffset;

  // helper properties
  private EnhancedCauldronBlock cauldronBlock;
  /** Last recipe used in the cauldron */
  private ICauldronRecipe lastRecipe;

  // temperature cache
  /** If true, the cauldron is above a block that makes it boil */
  private Boolean isBoiling;
  /** If true, the cauldron is surrounded by blocks that make it freeze */
  private Boolean isFreezing;
  /** Last temperature of the cauldron */
  private CauldronTemperature temperature;

  // transform recipes
  private int timer;
  /** Name of the in progress transform recipe */
  private ResourceLocation currentTransformName;
  /** Transform recipe currently in progress */
  private ICauldronTransform currentTransform;
  /** Last successful transform found */
  private ICauldronTransform lastTransform;
  /** If true, updates the transform recipe during the next tick */
  private boolean updateTransform;

  /**
   * Creates a new cauldron with no block set
   */
  public CauldronTileEntity() {
    this(InspirationsRecipes.cauldron);
  }

  /**
   * Creates a new cauldron for the given block
   * @param block  Parent block
   */
  public CauldronTileEntity(EnhancedCauldronBlock block) {
    this(InspirationsRecipes.tileCauldron, block);
  }

  /**
   * Extendable constructor to swap tile entity type
   * @param type   Tile entity type
   * @param block  Parent block
   */
  protected CauldronTileEntity(TileEntityType<?> type, EnhancedCauldronBlock block) {
    super(type);
    this.contents = CauldronContentTypes.DEFAULT.get();
    this.data.setData(TEXTURE, contents.getTextureName());
    this.cauldronBlock = block;
  }

  @Override
  public IModelData getModelData() {
    // ensure temperature is fetched so data is accurate
    getTemperature(false);
    return data;
  }


  /* contents and level */

  /**
   * Gets the current cauldron contents
   * @return current contents
   */
  public ICauldronContents getContents() {
    return contents;
  }

  /**
   * Gets the level of fluid in the cauldron, between 0 and {@link ICauldronRecipe#MAX}
   * @return  Cauldron fluid level
   */
  public int getLevel() {
    return cauldronBlock.getLevel(getBlockState()) * 4 + levelOffset;
  }

  /**
   * Checks if this cauldron can mimic vanilla, meaning other handlers are allowed to run. There are two problematic cases:
   * * Contents are not water
   * * Level is between 1 and 3, as internally is that 1 in the block state (has water), but not a full "bottle", causing other mods to see more water than expected
   * @return  True if this cauldron can redirect recipes to other handlers
   */
  public boolean canMimicVanilla() {
    return levelOffset >= 0 && contents.isSimple();
  }

  /**
   * Called when the state, contents, level offset, or temperature changes to handle recipe updates
   */
  private void contentsChanged() {
    this.updateTransform = true;
    //this.itemHandler.clearCache();
    //this.tank.clearCache();
  }

  /**
   * Updates the cauldron state, including the block state level
   * @param contents  New contents, null for no change
   * @param level     New levels
   */
  public void updateStateAndBlock(@Nullable ICauldronContents contents, int level) {
    int stateLevel = updateStateFromLevels(contents, level);
    if (world != null) {
      cauldronBlock.setWaterLevel(world, pos, getBlockState(), stateLevel);
    }
  }

  /**
   * Updates the state of the cauldron based on the given contents and levels and syncs to client.
   * Used in a few cases where state level update must be delayed
   * @param contents  New contents, null for no change
   * @param level     New level between 0 and 12. Set to -1 for no change
   * @return  New level for the block state
   */
  protected int updateStateFromLevels(@Nullable ICauldronContents contents, int level) {
    // first, determine the new level and contents
    // empty is empty of course
    int stateLevel, newOffset;
    if (level == 0) {
      newOffset = 0;
      stateLevel = 0;
    }
    else if (level < 4) {
      // if between 1 and 3, apply a negative offset
      newOffset = level - 4;
      stateLevel = 1;
    } else {
      // apply positive offsets otherwise
      newOffset = level % 4;
      stateLevel = level / 4;
    }

    // update TE props
    updateState(contents, newOffset);

    // return new level value
    return stateLevel;
  }

  /**
   * Updates just TE internal properties.
   * Used for transform recipe updates.
   * @param contents     New contents, null for no change
   * @param levelOffset  New level offset, between -3 and 3
   */
  protected void updateState(@Nullable ICauldronContents contents, int levelOffset) {
    // if the contents changed, update
    if (contents == null || this.contents.equals(contents)) {
      // set to null to signify no change, saves packet size
      contents = null;
    } else {
      this.contents = contents;
    }

    // if either changed, send a packet
    if (levelOffset != this.levelOffset || contents != null) {
      this.levelOffset = levelOffset;
      InspirationsNetwork.sendToClients(world, pos, new CauldronStateUpdatePacket(pos, contents, levelOffset));
      this.contentsChanged();
    }
  }

  /**
   * Updates contents and level offset in the TE and model data
   * @param contents     New contents, null for no change
   * @param levelOffset  New level offset
   * @return  True if anything changed
   */
  public boolean updateStateAndData(@Nullable ICauldronContents contents, int levelOffset) {
    boolean updated = false;

    // update offset and contents in TE and model data
    if (levelOffset != this.levelOffset) {
      this.levelOffset = levelOffset;
      data.setData(OFFSET, levelOffset);
      updated = true;
    }
    if (contents != null && !this.contents.equals(contents)) {
      this.contents = contents;
      data.setData(TEXTURE, contents.getTextureName());
      updated = true;
    }

    // return if we changed anything
    return updated;
  }


  /* temperature */

  /**
   * Checks if a state is considered fire in a cauldron
   * @param state State to check
   * @return True if the state is considered fire
   */
  public static boolean isCauldronFire(BlockState state) {
    if (state.getBlock().isIn(InspirationsTags.Blocks.CAULDRON_FIRE)) {
      // if it has a lit property, use that (campfires, furnaces). Otherwise just needs to be in the tag
      return !state.hasProperty(BlockStateProperties.LIT) || state.get(BlockStateProperties.LIT);
    }
    return false;
  }

  /**
   * Checks if the given direction has enough ice
   * @param world      World
   * @param pos        Position
   * @param direction  Direction to check. Also checks opposite direction
   * @return  True if the direction has enough ice
   */
  private static boolean isDirectionFreezing(World world, BlockPos pos, Direction direction) {
    return world.getBlockState(pos.offset(direction)).isIn(InspirationsTags.Blocks.CAULDRON_ICE)
           && world.getBlockState(pos.offset(direction.getOpposite())).isIn(InspirationsTags.Blocks.CAULDRON_ICE);
  }

  /**
   * Checks if two ice blocks on opposite sides
   * @param world  World
   * @param pos    Cauldron position
   * @return True if the state is considered freezing
   */
  public static boolean isFreezing(World world, BlockPos pos) {
    // either axis must have two ice blocks
    return isDirectionFreezing(world, pos, Direction.NORTH) || isDirectionFreezing(world, pos, Direction.WEST);
  }

  /**
   * Calculates and caches the temperature
   * @return  Calculated temperature for the world and positions
   */
  public static CauldronTemperature calcTemperature(IWorld world, BlockPos pos, boolean boiling, boolean freezing) {
    // overrides from neighbors
    if (boiling) {
      return freezing ? CauldronTemperature.NORMAL : CauldronTemperature.BOILING;
    }
    // freezing is freezing of course
    if (freezing) return CauldronTemperature.FREEZING;

    // boil if water evaporates
    if (world.getDimensionType().isUltrawarm()) {
      return CauldronTemperature.BOILING;
    }
    // freeze if biome is cold enough for snow/ice. direct methods do a bunch of ice/snow checks
    if (world.getBiome(pos).getTemperature(pos) < 0.15F) {
      return CauldronTemperature.FREEZING;
    }
    // normal otherwise
    return CauldronTemperature.NORMAL;
  }

  /**
   * Gets the current cauldron temperature
   * @param updateModelData  If true, updates model data on change
   * @return  Temperature at current location
   */
  private CauldronTemperature getTemperature(boolean updateModelData) {
    if (world == null) {
      return CauldronTemperature.NORMAL;
    }
    // if no temperature cache, calculate
    if (temperature == null) {
      // ensure we have cached freezing and boiling
      if (isBoiling == null) isBoiling = isCauldronFire(world.getBlockState(pos.down()));
      if (isFreezing == null) isFreezing = isFreezing(world, pos);
      temperature = calcTemperature(world, pos, isBoiling, isFreezing);
      data.setData(FROSTED, temperature == CauldronTemperature.FREEZING);
      if (updateModelData) requestModelDataUpdate();
    }
    // return cached value
    return temperature;
  }

  /**
   * Gets the current cauldron temperature
   * @return  Temperature at current location
   */
  public CauldronTemperature getTemperature() {
    return getTemperature(true);
  }


  /**
   * Gets the direction based on the given offset
   * @param offset  Offset
   * @return  Direction based on position offset
   */
  private static Direction getDirection(BlockPos offset) {
    for (Direction direction : Direction.values()) {
      if (direction.getDirectionVec().equals(offset)) {
        return direction;
      }
    }
    return Direction.UP;
  }

  /**
   * Called when a neighbor changes to invalidate the temperature cache
   * @param neighbor  Neighbor that changed
   */
  public void neighborChanged(BlockPos neighbor) {
    Direction direction = getDirection(neighbor.subtract(pos));
    CauldronTemperature oldTemperature = temperature;
    if (direction == Direction.DOWN) {
      isBoiling = null;
      temperature = null;
      this.contentsChanged();
    } else if (direction.getAxis() != Axis.Y) {
      isFreezing = null;
      temperature = null;
      this.contentsChanged();
    }
    // on the client, immediately update temperature
    if (world != null && world.isRemote) {
      temperature = getTemperature();
      if (temperature != oldTemperature) {
        Util.notifyClientUpdate(this);
      }
    }
  }


  /* behavior */

  @Nullable
  public ICauldronRecipe findRecipe() {
    if (world == null) {
      return null;
    }
    // try last recipe first
    if (lastRecipe != null && lastRecipe.matches(craftingInventory, world)) {
      return lastRecipe;
    }
    // fall back to finding a new recipe
    ICauldronRecipe recipe = world.getRecipeManager().getRecipe(RecipeTypes.CAULDRON, craftingInventory, world).orElse(null);
    if (recipe != null) {
      lastRecipe = recipe;
      return recipe;
    }
    // no recipe found
    return null;
  }

  /**
   * Handles a cauldron recipe. Will do everything except update the cauldron level and clear the context.
   * The caller is responsible for handling those (as each caller has different needs)
   * @param stack         Stack to match for recipes
   * @param itemSetter    Logic to update the stack in the context. If null, have to manually handle item setting (for dispensers)
   * @param itemAdder     Logic to add a new stack to the context
   * @return  True if the recipe matched, false otherwise
   */
  private boolean handleRecipe(ItemStack stack, @Nullable Consumer<ItemStack> itemSetter, Consumer<ItemStack> itemAdder) {
    if (world == null) {
      return false;
    }

    // update the stack context
    craftingInventory.setItemContext(stack, itemSetter, itemAdder);

    // grab recipe
    ICauldronRecipe recipe = findRecipe();
    boolean success = false;
    if (recipe != null) {
      success = true;
      if (!world.isRemote) {
        recipe.handleRecipe(craftingInventory);
      }
    }
    return success;
  }

  /**
   * Method to run cauldron interaction code
   * @return True if successful, false for pass
   */
  public boolean interact(PlayerEntity player, Hand hand) {
    // ensure we have a stack, or we can be done
    if (world == null) {
      return false;
    }

    // handle the recipe using the common function
    boolean success = handleRecipe(player.getHeldItem(hand), stack -> player.setHeldItem(hand, stack), CauldronItemInventory.getPlayerAdder(player));
    if (success) {
      updateStateAndBlock(craftingInventory.getContents(), craftingInventory.getLevel());
    }
    craftingInventory.clearContext();
    return success;
  }

  /**
   * Logic to run when a dispenser interacts with the cauldron
   * @param stack      Stack in the dispenser
   * @param itemAdder  Logic to add items to the dispenser
   * @return  Item stack after running the recipe, or null if no recipe ran
   */
  @Nullable
  public ItemStack handleDispenser(ItemStack stack, Consumer<ItemStack> itemAdder) {
    if (world == null) {
      return null;
    }

    // update level from the recipe and return the updated stack
    ItemStack result = null;
    if (handleRecipe(stack, null, itemAdder)) {
      updateStateAndBlock(craftingInventory.getContents(), craftingInventory.getLevel());
      result = craftingInventory.getStack();
    }
    craftingInventory.clearContext();
    return result;
  }

  private static final String TAG_CAULDRON_CRAFTED = "cauldron_crafted";
  private static final String TAG_CAULDRON_COOLDOWN = "cauldron_cooldown";

  /**
   * Called when an entity collides with the cauldron
   * @param entity Entity that collided
   * @param level  Cauldron level
   * @return New cauldron level after the collision
   */
  public int onEntityCollide(Entity entity, int level, BlockState currentState) {
    if (world == null) {
      return level;
    }

    // if an entity item, try crafting with it
    if (entity instanceof ItemEntity && Config.cauldronRecipes.getAsBoolean()) {
      // skip items that we have already processed
      ItemEntity entityItem = (ItemEntity)entity;
      CompoundNBT entityTags = entity.getPersistentData();
      // if it was tagged, skip it
      if (entityTags.getBoolean(TAG_CAULDRON_CRAFTED)) {
        return level;
      }

      // otherwise, if it has a cooldown, reduce the cooldown
      int cooldown = entityTags.getInt(TAG_CAULDRON_COOLDOWN);
      if (cooldown > 0) {
        entityTags.putInt(TAG_CAULDRON_COOLDOWN, cooldown - 1);
        return level;
      }

      // run recipe.
      // We need to copy when setting the item, to force it to update.
      boolean success = handleRecipe(entityItem.getItem(), stack -> entityItem.setItem(stack.copy()), stack -> {
        ItemEntity newItem = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        newItem.getPersistentData().putBoolean(CauldronTileEntity.TAG_CAULDRON_CRAFTED, true);
        newItem.setDefaultPickupDelay();
        world.addEntity(newItem);
      });

      // on success, run the recipe a few more times
      if (success) {
        int matches = 0;
        while (lastRecipe.matches(craftingInventory, world) && matches < 64) {
          lastRecipe.handleRecipe(craftingInventory);
          matches++;
        }

        // safety check, recipes should never really match more than 4 times, but 64 just in case someone does a item change without updating state (64 is a stack)
        // basically, they should either be lowering/raising the level (max 4 times), or changing the state (not repeatable)
        if (matches == 64) {
          Inspirations.log.warn("Recipe '{}' matched too many times in a single tick. Either the level or the state should change to make it no longer match.", lastRecipe.getId());
        }
      }

      // kill entity if empty
      if (entityItem.getItem().isEmpty()) {
        entityItem.remove();
      } else if (success) {
        // if the recipe worked, mark as crafted
        entityTags.putBoolean(TAG_CAULDRON_CRAFTED, true);
      } else {
        // set a cooldown to reduce lag, so we are not searching the registry every tick
        // we do not just set crafted as that would prevent dropping in items one at a time where multiple are required
        entityTags.putInt(TAG_CAULDRON_COOLDOWN, 60);
      }

      // return the final level update
      int stateLevel = updateStateFromLevels(craftingInventory.getContents(), craftingInventory.getLevel());
      craftingInventory.clearContext();
      return stateLevel;
    } else if (level > 0) {
      Optional<Fluid> fluidType = contents.get(CauldronContentTypes.FLUID);
      if (fluidType.isPresent()) {
        // water puts out fire
        Fluid fluid = fluidType.get();
        if (FluidTags.WATER.contains(fluid)) {
          if (entity.isBurning()) {
            entity.extinguish();
            level = level - 1;
          }
        }

        // hot fluids set fire to the entity
        else if (fluid.getAttributes().getTemperature() > 450 && !entity.isImmuneToFire()) {
          entity.attackEntityFrom(DamageSource.LAVA, 4.0F);
          entity.setFire(15);
          return level;
        }
      } else {
        // potions apply potion effects
        Optional<Potion> potionType = contents.get(CauldronContentTypes.POTION);
        if (potionType.isPresent() && entity instanceof LivingEntity) {
          LivingEntity living = (LivingEntity)entity;

          // if any of the effects are not currently on the player, apply it and lower the level
          List<EffectInstance> effects = potionType.get().getEffects();
          if (effects.stream().anyMatch(effect -> !living.isPotionActive(effect.getPotion()))) {
            for (EffectInstance effect : effects) {
              if (effect.getPotion().isInstant()) {
                effect.getPotion().affectEntity(null, null, living, effect.getAmplifier(), 1.0D);
              } else {
                living.addPotionEffect(new EffectInstance(effect));
              }
            }
            level = level - 1;
          }
          return level;
        }
      }

      // if the cauldron is boiling, boiling the entity
      if (getTemperature() == CauldronTemperature.BOILING) {
        entity.attackEntityFrom(DAMAGE_BOIL, 2.0F);
      }
    }
    return level;
  }

  /* Transform recipes */

  @Override
  public void updateContainingBlockInfo() {
    super.updateContainingBlockInfo();
    this.contentsChanged();
  }

  /**
   * Update the in progress transform recipe
   */
  public void updateTransform() {
    // stop if we have a non-loaded transform
    if (currentTransformName != null) {
      return;
    }

    // if the current transform matches, do nothing
    if (world == null || (currentTransform != null && currentTransform.matches(craftingInventory, world))) {
      return;
    }

    // recipe changing means reset the timer
    timer = 0;

    // try to find a recipe
    ICauldronTransform transform = null;
    if (getLevel() > 0) {
      if (lastTransform != null && lastTransform.matches(craftingInventory, world)) {
        transform = lastTransform;
      } else {
        Optional<ICauldronTransform> newTransform = world.getRecipeManager().getRecipe(RecipeTypes.CAULDRON_TRANSFORM, craftingInventory, world);
        if (newTransform.isPresent()) {
          transform = lastTransform = newTransform.get();
        }
      }
    }

    // handles both null mostly, but also the odd case of it matching again
    if (currentTransform != transform) {
      // update and sync to clients
      currentTransform = transform;
      InspirationsNetwork.sendToClients(world, pos, new CauldronTransformUpatePacket(pos, transform));
    }
  }

  @Override
  public void tick() {
    if (world == null) {
      return;
    }

    // updates the transform recipe
    if (updateTransform && !world.isRemote) {
      this.updateTransform();
      updateTransform = false;
    }

    // if we have a recipe
    if (currentTransform == null) {
      return;
    }

    // timer updates on both sides, easier than syncing
    timer++;

    // if the recipe is done, run recipe
    if (!world.isRemote && timer >= currentTransform.getTime()) {
      timer = 0;

      // play sound effect, note its before contents update
      SoundEvent sound = currentTransform.getSound();
      world.playSound(null, pos, sound, SoundCategory.BLOCKS, 1.0f, 1.0f);

      // set contents will clear the current transform if no longer current
      // have to pass in level offset as this function is reused a lot, so just use current
      updateState(currentTransform.getContentOutput(craftingInventory), levelOffset);
    }
  }

  /**
   * Called on the client to update the current transform recipe
   * @param recipe  New recipe
   */
  public void setTransformRecipe(@Nullable ICauldronTransform recipe) {
    this.currentTransform = recipe;
    timer = 0;
  }

  /**
   * Gets the number of fluid transform particles to display
   * @return  Particle count, 0 for no particles
   */
  public int getTransformParticles() {
    if (currentTransform == null) {
      return 0;
    }
    return timer * 5 / currentTransform.getTime();
  }

  /*
   * Called when the cauldron is broken
   * @param pos   Position of the cauldron
   * @param level Cauldron level
   *
  public void onBreak(BlockPos pos, int level) {
    if (world == null) {
      return;
    }
    switch (getContentType()) {
      case FLUID:
        BlockState block = state.getFluid().getAttributes().getBlock(null, null, state.getFluid().getDefaultState());
        if (block != null) {
          // height varies based on what is left. Will place a source if the cauldron is full
          if (level == (Config.enableBiggerCauldron() ? 4 : 3)) {
            world.setBlockState(pos, block);
          }
        }
        break;
      case POTION:
        Potion potion = state.getPotion();
        AreaEffectCloudEntity cloud = new AreaEffectCloudEntity(this.world, pos.getX() + 0.5F, pos.getY() + 0.5F, pos.getZ() + 0.5F);
        cloud.setRadius(0.5F * level + 0.5F);
        cloud.setDuration(20 * (level + 1));
        cloud.setRadiusOnUse(-0.5F);
        cloud.setWaitTime(10);
        cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());
        cloud.setPotion(potion);

        for (EffectInstance effect : potion.getEffects()) {
          cloud.addEffect(new EffectInstance(effect));
        }

        this.world.addEntity(cloud);
        break;
    }
  }
   */

  /* Automation */

  /* TODO: see above
  @Override
  public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
    if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
      return itemHandlerCap.cast();
    }
    if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
      return fluidHandlerCap.cast();
    }
    return super.getCapability(cap, side);
  }

  @Override
  protected void invalidateCaps() {
    super.invalidateCaps();
    itemHandlerCap.invalidate();
    fluidHandlerCap.invalidate();
  }
  */

  /* NBT */
  private static final String TAG_CONTENTS = "contents";
  private static final String TAG_LEVEL_OFFSET = "level_offset";
  private static final String TAG_TRANSFORM = "transform";
  private static final String TAG_TIMER = "timer";

  @Override
  public void setWorldAndPos(World world, BlockPos pos) {
    super.setWorldAndPos(world, pos);
    // if we have a recipe name, swap recipe name for recipe instance
    if (currentTransformName != null) {
      loadTransform(world, currentTransformName);
      currentTransformName = null;
    }
  }

  /**
   * Updates the current transform based on the given name
   * @param world  World instance
   * @param name   Recipe name
   */
  private void loadTransform(World world, ResourceLocation name) {
    RecipeHelper.getRecipe(world.getRecipeManager(), name, ICauldronTransform.class).ifPresent(recipe -> this.currentTransform = recipe);
  }

  @Override
  public CompoundNBT getUpdateTag() {
    // new tag instead of super since default implementation calls the super of writeToNBT
    return write(new CompoundNBT());
  }

  @Override
  public CompoundNBT write(CompoundNBT tags) {
    tags = super.write(tags);
    tags.put(TAG_CONTENTS, getContents().toNBT());
    // write transform if present, or transform name if we somehow wrote before world is set
    if (currentTransform != null) {
      tags.putString(TAG_TRANSFORM, currentTransform.getId().toString());
    } else if (currentTransformName != null) {
      tags.putString(TAG_TRANSFORM, currentTransformName.toString());
    }
    // update the timer from NBT
    tags.putInt(TAG_TIMER, timer);
    tags.putInt(TAG_LEVEL_OFFSET, levelOffset);
    return tags;
  }

  @Override
  public void read(BlockState state, CompoundNBT tags) {
    super.read(state, tags);

    // update block reference
    Block block = state.getBlock();
    if (block instanceof EnhancedCauldronBlock) {
      this.cauldronBlock = (EnhancedCauldronBlock)block;
    }

    // update current transform
    if (tags.contains(TAG_TRANSFORM, NBT.TAG_STRING)) {
      ResourceLocation name = new ResourceLocation(tags.getString(TAG_TRANSFORM));
      // if we have a world, fetch the recipe
      if (world != null) {
        loadTransform(world, name);
      } else {
        // otherwise fetch the recipe when the world is set
        currentTransformName = name;
      }
    }

    // update contents
    updateStateAndData(CauldronContentTypes.read(tags.getCompound(TAG_CONTENTS)), tags.getInt(TAG_LEVEL_OFFSET));

    // update the timer from NBT
    timer = tags.getInt(TAG_TIMER);
  }
}
