package com.moysecamm.minecleaner.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MineCleanerSettingsScreen extends Screen {

	private final Screen returnTo;
	private Button languageButton;

	public MineCleanerSettingsScreen(Screen returnTo) {
		super(MineCleanerConfig.text("minecleaner.settings.title"));
		this.returnTo = returnTo;
	}

	@Override
	public void onClose() {
		MineCleanerConfig.get().save();
		Minecraft mc = Minecraft.getInstance();
		if (returnTo != null && returnTo != this) {
			mc.setScreenAndShow(returnTo);
		} else {
			super.onClose();
		}
	}

	@Override
	protected void init() {
		MineCleanerConfig cfg = MineCleanerConfig.get();
		int cx = this.width / 2 - 120;
		int cy = 40;
		int row = 24;

		addCheckbox(cx, cy, "minecleaner.settings.worlds", cfg.enableWorlds, val -> cfg.enableWorlds = val);
		addCheckbox(cx, cy + row, "minecleaner.settings.servers", cfg.enableServers, val -> cfg.enableServers = val);
		addCheckbox(cx, cy + row * 2, "minecleaner.settings.packs", cfg.enablePacks, val -> cfg.enablePacks = val);
		addCheckbox(cx, cy + row * 3, "minecleaner.settings.shaders", cfg.enableShaders, val -> cfg.enableShaders = val);

		languageButton = Button.builder(languageLabel(), b -> {
			MineCleanerConfig c = MineCleanerConfig.get();
			int idx = MineCleanerConfig.langIndex(c.language);
			c.language = MineCleanerConfig.LANG_CODES[(idx + 1) % MineCleanerConfig.LANG_CODES.length];
			c.save();
			rebuild();
		}).bounds(cx, cy + row * 4 + 8, 240, 20).build();
		addRenderableWidget(languageButton);

		addRenderableWidget(Button.builder(sortLabel(), b -> {
			MineCleanerConfig c = MineCleanerConfig.get();
			c.worldSort = "date".equals(c.worldSort) ? "name" : "date";
			c.save();
			rebuild();
		}).bounds(cx, cy + row * 5 + 8, 240, 20).build());

		addRenderableWidget(Button.builder(
				MineCleanerConfig.text("minecleaner.settings.done"),
				b -> onClose()
		).bounds(this.width / 2 - 60, this.height - 38, 120, 20).build());
	}

	private void addCheckbox(int x, int y, String key, boolean selected, java.util.function.Consumer<Boolean> apply) {
		Checkbox cb = Checkbox.builder(MineCleanerConfig.text(key), Minecraft.getInstance().font)
				.pos(x, y)
				.selected(selected)
				.onValueChange((box, val) -> {
					apply.accept(val);
					MineCleanerConfig.get().save();
				})
				.build();
		addRenderableWidget(cb);
	}

	private void rebuild() {
		clearWidgets();
		init();
	}

	private Component languageLabel() {
		return MineCleanerConfig.text("minecleaner.settings.language").copy()
				.append(Component.literal(": " + langNameLocal()));
	}

	private Component sortLabel() {
		String mode = MineCleanerConfig.get().worldSort;
		String valueKey = "date".equals(mode) ? "minecleaner.sort.date" : "minecleaner.sort.name";
		return MineCleanerConfig.text("minecleaner.settings.sort").copy()
				.append(Component.literal(": " + MineCleanerConfig.text(valueKey).getString()));
	}

	private String langNameLocal() {
		String code = MineCleanerConfig.get().language;
		if ("system".equals(code)) {
			return MineCleanerConfig.text("minecleaner.settings.system").getString();
		}
		return MineCleanerConfig.langName(code);
	}
}