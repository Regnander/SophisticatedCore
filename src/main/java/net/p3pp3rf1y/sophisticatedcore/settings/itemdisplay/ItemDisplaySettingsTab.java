package net.p3pp3rf1y.sophisticatedcore.settings.itemdisplay;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.inventory.Slot;
import net.p3pp3rf1y.sophisticatedcore.client.gui.SettingsScreen;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.Button;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ButtonDefinition;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ImageButton;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Dimension;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.GuiHelper;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.TextureBlitData;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.TranslationHelper;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.UV;
import net.p3pp3rf1y.sophisticatedcore.settings.ColorToggleButton;
import net.p3pp3rf1y.sophisticatedcore.settings.SettingsTab;
import net.p3pp3rf1y.sophisticatedcore.util.ColorHelper;

import java.util.List;
import java.util.Optional;

import static net.p3pp3rf1y.sophisticatedcore.client.gui.utils.GuiHelper.DEFAULT_BUTTON_BACKGROUND;
import static net.p3pp3rf1y.sophisticatedcore.client.gui.utils.GuiHelper.DEFAULT_BUTTON_HOVERED_BACKGROUND;

public class ItemDisplaySettingsTab extends SettingsTab<ItemDisplaySettingsContainer> {
	private static final TextureBlitData ICON = new TextureBlitData(GuiHelper.ICONS, Dimension.SQUARE_256, new UV(112, 64), Dimension.SQUARE_16);
	private static final TextureBlitData SLOT_SELECTION = new TextureBlitData(GuiHelper.GUI_CONTROLS, Dimension.SQUARE_256, new UV(93, 0), Dimension.SQUARE_24);
	private static final List<Component> ROTATE_TOOLTIP = new ImmutableList.Builder<Component>()
			.add(new TranslatableComponent(TranslationHelper.INSTANCE.translSettingsButton("rotate")))
			.addAll(TranslationHelper.INSTANCE.getTranslatedLines(TranslationHelper.INSTANCE.translSettingsButton("rotate_detail"), null, ChatFormatting.GRAY))
			.build();
	private static final TextureBlitData ROTATE_FOREGROUND = new TextureBlitData(GuiHelper.ICONS, new Position(1, 1), Dimension.SQUARE_256, new UV(128, 64), Dimension.SQUARE_16);
	public static final ButtonDefinition ROTATE = new ButtonDefinition(Dimension.SQUARE_16, DEFAULT_BUTTON_BACKGROUND, DEFAULT_BUTTON_HOVERED_BACKGROUND, ROTATE_FOREGROUND);

	private int currentSelectedSlot = -1;

	public ItemDisplaySettingsTab(ItemDisplaySettingsContainer container, Position position, SettingsScreen screen) {
		super(container, position, screen, new TranslatableComponent(TranslationHelper.INSTANCE.translSettings(ItemDisplaySettingsCategory.NAME)),
				new ImmutableList.Builder<Component>()
						.add(new TranslatableComponent(TranslationHelper.INSTANCE.translSettingsTooltip(ItemDisplaySettingsCategory.NAME)))
						.addAll(TranslationHelper.INSTANCE.getTranslatedLines(TranslationHelper.INSTANCE.translSettingsTooltip(ItemDisplaySettingsCategory.NAME) + "_detail", null, ChatFormatting.GRAY))
						.build(),
				new ImmutableList.Builder<Component>()
						.add(new TranslatableComponent(TranslationHelper.INSTANCE.translSettingsTooltip(ItemDisplaySettingsCategory.NAME)))
						.addAll(TranslationHelper.INSTANCE.getTranslatedLines(TranslationHelper.INSTANCE.translSettingsTooltip(ItemDisplaySettingsCategory.NAME) + "_open_detail", null, ChatFormatting.GRAY))
						.build(),
				onTabIconClicked -> new ImageButton(new Position(position.x() + 1, position.y() + 4), Dimension.SQUARE_16, ICON, onTabIconClicked));
		addHideableChild(new Button(new Position(x + 3, y + 24), ROTATE, button -> {
			if (button == 0) {
				container.rotateClockwise(currentSelectedSlot);
			} else if (button == 1) {
				container.rotateCounterClockwise(currentSelectedSlot);
			}
		}) {
			@Override
			protected List<Component> getTooltip() {
				return ROTATE_TOOLTIP;
			}
		});
		addHideableChild(new ColorToggleButton(new Position(x + 21, y + 24), container::getColor, container::setColor));
		currentSelectedSlot = getSettingsContainer().getFirstSelectedSlot();
	}

	@Override
	public Optional<Integer> getSlotOverlayColor(int slotNumber) {
		return getSettingsContainer().isSlotSelected(slotNumber) ? Optional.of(ColorHelper.getColor(getSettingsContainer().getColor().getTextureDiffuseColors()) | (80 << 24)) : Optional.empty();
	}

	@Override
	public void handleSlotClick(Slot slot, int mouseButton) {
		if (mouseButton == 0) {
			getSettingsContainer().selectSlot(slot.index);
			if (getSettingsContainer().isSlotSelected(slot.index)) {
				currentSelectedSlot = slot.index;
			}
		} else if (mouseButton == 1) {
			getSettingsContainer().unselectSlot(slot.index);
			if (!getSettingsContainer().isSlotSelected(slot.index) && currentSelectedSlot == slot.index) {
				currentSelectedSlot = getSettingsContainer().getFirstSelectedSlot();
			}
		}
	}

	@Override
	public void renderExtra(PoseStack poseStack, Slot slot) {
		super.renderExtra(poseStack, slot);
		if (isOpen && slot.index == currentSelectedSlot) {
			RenderSystem.disableDepthTest();
			RenderSystem.colorMask(true, true, true, false);
			GuiHelper.blit(poseStack, slot.x - 4, slot.y - 4, SLOT_SELECTION);
			RenderSystem.colorMask(true, true, true, true);
			RenderSystem.enableDepthTest();
		}
	}

	@Override
	public int getItemRotation(int slotIndex) {
		return getSettingsContainer().getRotation(slotIndex);
	}
}
