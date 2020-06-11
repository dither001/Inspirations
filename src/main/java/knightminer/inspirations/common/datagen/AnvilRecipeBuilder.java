package knightminer.inspirations.common.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import knightminer.inspirations.library.Util;
import knightminer.inspirations.library.recipe.BlockIngredient;
import knightminer.inspirations.library.recipe.anvil.AnvilRecipe;
import net.minecraft.advancements.criterion.StatePropertiesPredicate;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.data.IFinishedRecipe;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.state.IProperty;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tags.Tag;
import net.minecraft.util.IItemProvider;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AnvilRecipeBuilder {
	private final List<Ingredient> ingredients;
	private final List<Pair<String, String>> properties;
	private String group = "";
	@Nullable
	private final Block result;

	private AnvilRecipeBuilder(@Nullable Block res) {
		result = res;
		ingredients = new ArrayList<>();
		properties = new ArrayList<>();
	}

	public static AnvilRecipeBuilder copiesInput() {
		return new AnvilRecipeBuilder(null);
	}

	public static AnvilRecipeBuilder places(@Nonnull Block block) {
		return new AnvilRecipeBuilder(block);
	}

	public static AnvilRecipeBuilder smashes() {
		return new AnvilRecipeBuilder(Blocks.AIR);
	}

	public AnvilRecipeBuilder group(String name) {
		group = name;
		return this;
	}

	public AnvilRecipeBuilder addIngredient(Block block) {
		ingredients.add(new BlockIngredient.DirectBlockIngredient(block, StatePropertiesPredicate.EMPTY));
		return this;
	}

	public AnvilRecipeBuilder addIngredient(Block block, StatePropertiesPredicate pred) {
		ingredients.add(new BlockIngredient.DirectBlockIngredient(block, pred));
		return this;
	}

	public AnvilRecipeBuilder addIngredient(Tag<Block> blockTag) {
		ingredients.add(new BlockIngredient.TaggedBlockIngredient(blockTag, StatePropertiesPredicate.EMPTY));
		return this;
	}

	public AnvilRecipeBuilder addIngredient(Tag<Block> blockTag, StatePropertiesPredicate pred) {
		ingredients.add(new BlockIngredient.TaggedBlockIngredient(blockTag, pred));
		return this;
	}

	public AnvilRecipeBuilder addIngredient(IItemProvider item) {
		ingredients.add(Ingredient.fromItems(item));
		return this;
	}

	public AnvilRecipeBuilder addIngredient(IItemProvider item, int quantity) {
		for(int i = 0; i < quantity; i++) {
			ingredients.add(Ingredient.fromItems(item));
		}
		return this;
	}

	public AnvilRecipeBuilder copiesProperty(IProperty<?> prop) {
		properties.add(Pair.of(prop.getName(), AnvilRecipe.FROM_INPUT));
		return this;
	}

	public <T extends Comparable<T>> AnvilRecipeBuilder setsProp(IProperty<T> prop, T value) {
		properties.add(Pair.of(prop.getName(), value.toString()));
		return this;
	}

	/**
	 * Use IProperty versions if possible to ensure validity!
	 */
	public AnvilRecipeBuilder copiesPropertyUnsafe(String prop) {
		properties.add(Pair.of(prop, AnvilRecipe.FROM_INPUT));
		return this;
	}

	/**
	 * Use IProperty versions if possible to ensure validity!
	 */
	public AnvilRecipeBuilder setsPropUnsafe(String prop) {
		properties.add(Pair.of(prop, AnvilRecipe.FROM_INPUT));
		return this;
	}

	// Shortcuts for common property setups.
	public AnvilRecipeBuilder copiesStandardSlab() {
		return this
				.copiesProperty(BlockStateProperties.HALF)
				.copiesProperty(BlockStateProperties.WATERLOGGED);
	}

	// Shortcuts for common property setups.
	public AnvilRecipeBuilder copiesStandardStair() {
		return this
				.copiesProperty(BlockStateProperties.HALF)
				.copiesProperty(BlockStateProperties.STAIRS_SHAPE)
				.copiesProperty(BlockStateProperties.HORIZONTAL_FACING)
				.copiesProperty(BlockStateProperties.WATERLOGGED);
	}

	public void build(Consumer<IFinishedRecipe> consumer, ResourceLocation id) {
		if (ingredients.size() == 0) {
			throw new IllegalStateException("Recipe must have at least one ingredient!");
		}
		if (result == Blocks.AIR) {
			id = new ResourceLocation(id.getNamespace(), "anvil_smash_" + id.getPath());
		} else if (!id.getPath().contains("anvil")) {
			id = new ResourceLocation(id.getNamespace(), id.getPath() + "_from_anvil_smashing");
		}
		consumer.accept(new Finished(
				id,
				ingredients,
				result,
				properties,
				group
		));
	}

	public void buildInsp(Consumer<IFinishedRecipe> consumer, String id) {
		build(consumer, Util.getResource(id));
	}

	public void buildVanilla(Consumer<IFinishedRecipe> consumer, String id) {
		build(consumer, new ResourceLocation(id));
	}

	// Same name as the item.
	public void build(Consumer<IFinishedRecipe> consumer) {
		ResourceLocation id;
		if (result == null) {
			throw new IllegalStateException("Save location required for recipe which copies input block!");
		} else if (result == Blocks.AIR) {
			// Copy the first block input's ID.
			id = null;
			for(Ingredient ing: ingredients) {
				if (ing instanceof BlockIngredient.DirectBlockIngredient) {
					id = ((BlockIngredient.DirectBlockIngredient) ing).block.getRegistryName();
					break;
				} else if (ing instanceof BlockIngredient.TaggedBlockIngredient) {
					id = ((BlockIngredient.TaggedBlockIngredient) ing).tag.getId();
					break;
				}
			}
			if (id == null) {
				throw new IllegalStateException("Could not infer save location for smashing recipe!");
			}
		} else {
			id = result.getRegistryName();
		}
		build(consumer, id);
	}

	private static class Finished implements IFinishedRecipe {
		private final ResourceLocation id;
		private final List<Ingredient> ingredients;
		private final String group;
		// If null, keep the existing block.
		@Nullable
		private final Block result;
		// Properties to assign to the result
		private final List<Pair<String, String>> properties;

		private Finished(
				ResourceLocation id,
				List<Ingredient> ingredients,
				@Nullable Block result,
				List<Pair<String, String>> properties,
				String group
		) {
			this.id = id;
			this.ingredients = ingredients;
			this.group = group;
			this.result = result;
			this.properties = properties;
		}

		@Override
		public void serialize(@Nonnull JsonObject json) {
			if (!group.isEmpty()) {
				json.addProperty("group", group);
			}
			JsonObject result = new JsonObject();
			json.add("result", result);
			if (this.result == null) {
				result.addProperty("block", AnvilRecipe.FROM_INPUT);
			} else {
				result.addProperty("block", this.result.getRegistryName().toString());
			}
			if (properties.size() > 0) {
				JsonObject props = new JsonObject();
				for(Pair<String, String> prop : properties) {
					result.addProperty(prop.getFirst(), prop.getSecond());
				}
				result.add("properties", props);
			}

			JsonArray ingredients = new JsonArray();
			for(Ingredient ingredient : this.ingredients) {
				ingredients.add(ingredient.serialize());
			}
			json.add("ingredients", ingredients);
		}

		@Nonnull
		@Override
		public ResourceLocation getID() {
			return id;
		}

		@Nonnull
		@Override
		public IRecipeSerializer<?> getSerializer() {
			return AnvilRecipe.SERIALIZER;
		}

		// We can't add a recipe book to the anvil, so there's no advancement.
		@Override
		public JsonObject getAdvancementJson() {
			return null;
		}

		@Override
		public ResourceLocation getAdvancementID() {
			return null;
		}
	}
}
