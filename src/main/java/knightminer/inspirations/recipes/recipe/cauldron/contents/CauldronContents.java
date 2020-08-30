package knightminer.inspirations.recipes.recipe.cauldron.contents;

import knightminer.inspirations.library.recipe.cauldron.contents.CauldronContentType;
import knightminer.inspirations.library.recipe.cauldron.contents.ICauldronContents;
import net.minecraft.util.ResourceLocation;

import java.util.Optional;

/**
 * Content type implementation. Mainly out of the library as no one should be directly constructing this class
 * @param <C>  Content value type
 */
public class CauldronContents<C> implements ICauldronContents {
  private final CauldronContentType<C> type;
  private final C value;

  /**
   * Creates a new instance
   * @param type   Content type
   * @param value  Content value
   */
  public CauldronContents(CauldronContentType<C> type, C value) {
    this.type = type;
    this.value = value;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> Optional<T> get(CauldronContentType<T> type) {
    if (type == this.type) {
      return Optional.of((T)this.value);
    }
    return type.getOverrideValue(this);
  }

  @Override
  public CauldronContentType<?> getType() {
    return type;
  }

  @Override
  public ResourceLocation getTextureName() {
    return type.getTexture(value);
  }

  @Override
  public int getTintColor() {
    return type.getColor(value);
  }

  @Override
  public <T> boolean matches(CauldronContentType<T> type, T value) {
    return this.type == type && this.value == value;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ICauldronContents)) {
      return false;
    }
    ICauldronContents contents = (ICauldronContents)other;
    return contents.matches(type, value);
  }

  @Override
  public int hashCode() {
    return 31 * type.hashCode() + value.hashCode();
  }

  @Override
  public String toString() {
    return String.format("CauldronContents(%s,%s)", type.toString(), value.toString());
  }
}