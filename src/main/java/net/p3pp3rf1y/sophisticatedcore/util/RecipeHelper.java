package net.p3pp3rf1y.sophisticatedcore.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.p3pp3rf1y.sophisticatedcore.SophisticatedCore;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static net.p3pp3rf1y.sophisticatedcore.util.RecipeHelper.CompactingShape.*;

public class RecipeHelper {
	private static final LoadingCache<Item, Set<CompactingShape>> ITEM_COMPACTING_SHAPES = CacheBuilder.newBuilder().expireAfterAccess(10L, TimeUnit.MINUTES).build(new CacheLoader<>() {
		@Override
		public Set<CompactingShape> load(Item item) {
			SophisticatedCore.LOGGER.debug("Compacting shapes not found in cache for \"{}\" - querying recipes to get these", item.getRegistryName());
			return getCompactingShapes(item);
		}
	});
	private static final int MAX_FOLLOW_UP_COMPACTING_RECIPES = 30;
	private static WeakReference<Level> world;
	private static final Map<CompactedItem, CompactingResult> COMPACTING_RESULTS = new HashMap<>();

	private RecipeHelper() {}

	public static void setWorld(Level w) {
		world = new WeakReference<>(w);
	}

	private static Optional<Level> getWorld() {
		return Optional.ofNullable(world.get());
	}

	private static Set<CompactingShape> getCompactingShapes(Item item) {
		return getWorld().map(w -> {
			Set<CompactingShape> compactingShapes = new HashSet<>();
			getCompactingShape(item, w, 2, 2, TWO_BY_TWO_UNCRAFTABLE, TWO_BY_TWO).ifPresent(compactingShapes::add);
			getCompactingShape(item, w, 3, 3, THREE_BY_THREE_UNCRAFTABLE, THREE_BY_THREE).ifPresent(compactingShapes::add);
			if (compactingShapes.isEmpty()) {
				compactingShapes.add(NONE);
			}
			return compactingShapes;
		}).orElse(Collections.emptySet());
	}

	private static Optional<CompactingShape> getCompactingShape(Item item, Level w, int width, int height, CompactingShape uncraftableShape, CompactingShape shape) {
		CompactingResult compactingResult = getCompactingResult(item, w, width, height);
		if (!compactingResult.getResult().isEmpty()) {
			if (item == compactingResult.getResult().getItem()) {
				return Optional.empty();
			}

			if (isPartOfCompactingLoop(item, compactingResult.getResult().getItem(), w)) {
				return Optional.empty();
			}

			if (uncompactMatchesItem(compactingResult.getResult(), w, item, width * height)) {
				return Optional.of(uncraftableShape);
			} else {
				return Optional.of(shape);
			}
		}
		return Optional.empty();
	}

	private static boolean isPartOfCompactingLoop(Item firstCompacted, Item firstCompactResult, Level w) {
		ItemStack compactingResultStack;
		int iterations = 0;
		Set<Item> compactedItems = new HashSet<>();
		Queue<Item> itemsToCompact = new LinkedList<>();
		itemsToCompact.add(firstCompactResult);
		while (!itemsToCompact.isEmpty()) {
			Item itemToCompact = itemsToCompact.poll();
			compactingResultStack = getCompactingResult(itemToCompact, w, 2, 2).getResult();
			if (!compactingResultStack.isEmpty()) {
				if (compactingResultStack.getItem() == firstCompacted) {
					return true;
				} else if (compactedItems.contains(compactingResultStack.getItem())) {
					return false; //loop exists but the first compacted item isn't part of it so we will let it be compacted, but no follow up compacting will happen
				}
				itemsToCompact.add(compactingResultStack.getItem());
			}

			compactingResultStack = getCompactingResult(itemToCompact, w, 3, 3).getResult();
			if (!compactingResultStack.isEmpty()) {
				if (compactingResultStack.getItem() == firstCompacted) {
					return true;
				} else if (compactedItems.contains(compactingResultStack.getItem())) {
					return false; //loop exists but the first compacted item isn't part of it so we will let it be compacted, but no follow up compacting will happen
				}
				itemsToCompact.add(compactingResultStack.getItem());
			}
			compactedItems.add(itemToCompact);
			iterations++;
			if (iterations > MAX_FOLLOW_UP_COMPACTING_RECIPES) {
				return true; //we were unable to figure out if the loop exists because of way too many follow up compacting recipe thus not allowing to compact anyway
			}
		}
		return false;
	}

	private static boolean uncompactMatchesItem(ItemStack result, Level w, Item item, int count) {
		CraftingContainer craftingInventory = getFilledCraftingInventory(result.getItem(), 1, 1);
		result = w.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftingInventory, w).map(r -> r.assemble(craftingInventory)).orElse(ItemStack.EMPTY);
		return result.getItem() == item && result.getCount() == count;
	}

	public static CompactingResult getCompactingResult(Item item, int width, int height) {
		return getWorld().map(w -> getCompactingResult(item, w, width, height)).orElse(CompactingResult.EMPTY);
	}

	private static CompactingResult getCompactingResult(Item item, Level w, int width, int height) {
		CompactedItem compactedItem = new CompactedItem(item, width, height);
		if (COMPACTING_RESULTS.containsKey(compactedItem)) {
			return COMPACTING_RESULTS.get(compactedItem);
		}

		CraftingContainer craftingInventory = getFilledCraftingInventory(item, width, height);
		List<ItemStack> remainingItems = new ArrayList<>();
		ItemStack result = w.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftingInventory, w).map(r -> {
			r.getRemainingItems(craftingInventory).forEach(stack -> {
				if (!stack.isEmpty()) {
					remainingItems.add(stack);
				}
			});
			return r.assemble(craftingInventory);
		}).orElse(ItemStack.EMPTY);

		CompactingResult compactingResult = new CompactingResult(result, remainingItems);
		if (!result.isEmpty()) {
			COMPACTING_RESULTS.put(compactedItem, compactingResult);
		}
		return compactingResult;
	}

	private static CraftingContainer getFilledCraftingInventory(Item item, int width, int height) {
		CraftingContainer craftinginventory = new CraftingContainer(new AbstractContainerMenu(null, -1) {
			public boolean stillValid(Player playerIn) {
				return false;
			}
		}, width, height);

		for (int i = 0; i < craftinginventory.getContainerSize(); i++) {
			craftinginventory.setItem(i, new ItemStack(item));
		}
		return craftinginventory;
	}

	public static <T extends AbstractCookingRecipe> Optional<T> getCookingRecipe(ItemStack stack, RecipeType<T> recipeType) {
		return getWorld().flatMap(w -> w.getRecipeManager().getRecipeFor(recipeType, new RecipeWrapper(new ItemStackHandler(NonNullList.of(ItemStack.EMPTY, stack))), w));
	}

	public static Set<CompactingShape> getItemCompactingShapes(Item item) {
		return ITEM_COMPACTING_SHAPES.getUnchecked(item);
	}

	public static List<StonecutterRecipe> getStonecuttingRecipes(Container inventory) {
		return getWorld().map(w -> w.getRecipeManager().getRecipesFor(RecipeType.STONECUTTING, inventory, w)).orElse(Collections.emptyList());
	}

	public enum CompactingShape {
		NONE,
		THREE_BY_THREE,
		TWO_BY_TWO,
		THREE_BY_THREE_UNCRAFTABLE,
		TWO_BY_TWO_UNCRAFTABLE
	}

	public static class CompactingResult {
		public static final CompactingResult EMPTY = new CompactingResult(ItemStack.EMPTY, Collections.emptyList());

		private final ItemStack result;
		private final List<ItemStack> remainingItems;

		private CompactingResult(ItemStack result, List<ItemStack> remainingItems) {
			this.result = result;
			this.remainingItems = remainingItems;
		}

		public ItemStack getResult() {
			return result;
		}

		public List<ItemStack> getRemainingItems() {
			return remainingItems;
		}
	}

	private static class CompactedItem {
		private final Item item;
		private final int width;
		private final int height;

		private CompactedItem(Item item, int width, int height) {
			this.item = item;
			this.width = width;
			this.height = height;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			CompactedItem that = (CompactedItem) o;
			return width == that.width &&
					height == that.height &&
					item.equals(that.item);
		}

		@Override
		public int hashCode() {
			return Objects.hash(item, width, height);
		}
	}
}
