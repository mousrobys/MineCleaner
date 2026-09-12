package com.moysecamm.minecleaner.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.moysecamm.minecleaner.util.DeletionHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MineCleanerClient implements ClientModInitializer {

    private static final Map<Screen, Set<Integer>> selectedMap = new IdentityHashMap<>();
    private static final Map<Screen, List<GuiEventListener>> addedMap = new IdentityHashMap<>();
    private static final Map<Screen, Button> deleteButtons = new IdentityHashMap<>();
    private static final Map<Screen, Integer> rangeAnchor = new IdentityHashMap<>();
    private static final Map<Screen, String> sortFingerprints = new IdentityHashMap<>();
    private static final Set<Screen> eventsRegistered = Collections.synchronizedSet(new HashSet<>());

    private static final Map<String, Long> sizeCache = new ConcurrentHashMap<>();
    private static final Set<String> sizePending = ConcurrentHashMap.newKeySet();
    private static final ExecutorService SIZE_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "minecleaner-size");
        t.setDaemon(true);
        return t;
    });
    private static final Map<Screen, String> buttonMsgState = new IdentityHashMap<>();

    private static Method addWidgetMethod;
    private static Method removeWidgetMethod;

    private static Field wsListField;
    private static Field wsDeleteField;
    private static Field wsSearchField;
    private static Field wsNameField;
    private static Field jsListField;
    private static Field jsDeleteField;
    private static Field psAvailableField;

    private static Class<?> shaderPackScreenClass;
    private static Class<?> shaderPackEntryClass;
    private static Field shaderListField;

    private static KeyMapping settingsKey;

    @Override
    public void onInitializeClient() {
        try {
            addWidgetMethod = Screen.class.getDeclaredMethod("addRenderableWidget", GuiEventListener.class);
            addWidgetMethod.setAccessible(true);
            removeWidgetMethod = Screen.class.getDeclaredMethod("removeWidget", GuiEventListener.class);
            removeWidgetMethod.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            wsListField = SelectWorldScreen.class.getDeclaredField("list");
            wsListField.setAccessible(true);
            wsDeleteField = SelectWorldScreen.class.getDeclaredField("deleteButton");
            wsDeleteField.setAccessible(true);
            wsSearchField = SelectWorldScreen.class.getDeclaredField("searchBox");
            wsSearchField.setAccessible(true);
            wsNameField = WorldSelectionList.WorldListEntry.class.getDeclaredField("worldNameText");
            wsNameField.setAccessible(true);
            jsListField = JoinMultiplayerScreen.class.getDeclaredField("serverSelectionList");
            jsListField.setAccessible(true);
            jsDeleteField = JoinMultiplayerScreen.class.getDeclaredField("deleteButton");
            jsDeleteField.setAccessible(true);
            psAvailableField = PackSelectionScreen.class.getDeclaredField("availablePackList");
            psAvailableField.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            shaderPackScreenClass = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
            shaderPackEntryClass = Class.forName("net.irisshaders.iris.gui.element.ShaderPackSelectionList$ShaderPackEntry");
            shaderListField = shaderPackScreenClass.getDeclaredField("shaderPackList");
            shaderListField.setAccessible(true);
        } catch (Throwable e) {
            shaderPackScreenClass = null;
        }

        settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.minecleaner.settings", InputConstants.Type.KEYSYM, InputConstants.KEY_K, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (settingsKey != null && settingsKey.consumeClick()) {
                client.setScreenAndShow(new MineCleanerSettingsScreen(client.gui.screen()));
            }
        });

        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            try {
                if (screen instanceof SelectWorldScreen ws) {
                    setupWorldScreen(ws);
                } else if (screen instanceof JoinMultiplayerScreen js) {
                    setupServerScreen(js);
                } else if (screen instanceof PackSelectionScreen ps) {
                    setupPackScreen(ps);
                } else if (shaderPackScreenClass != null && shaderPackScreenClass.isInstance(screen)) {
                    setupShaderScreen(screen);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        });
    }

    // ==================== HELPERS ====================

    private static Object getField(Field f, Object target) {
        try {
            return f != null ? f.get(target) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void addWidget(Screen screen, GuiEventListener widget) {
        try { addWidgetMethod.invoke(screen, widget); } catch (Exception e) { e.printStackTrace(); }
        if (widget instanceof net.minecraft.client.gui.components.Renderable) {
            addedMap.computeIfAbsent(screen, k -> new ArrayList<>()).add(widget);
        }
    }

    private static void removeWidget(Screen screen, GuiEventListener widget) {
        try { removeWidgetMethod.invoke(screen, widget); } catch (Exception e) { e.printStackTrace(); }
    }

    private static void clearAdded(Screen screen) {
        List<GuiEventListener> mine = addedMap.remove(screen);
        if (mine != null) {
            for (GuiEventListener w : mine) {
                try { removeWidgetMethod.invoke(screen, w); } catch (Exception e) {}
            }
        }
    }

    private static Set<Integer> sel(Screen s) {
        return selectedMap.computeIfAbsent(s, k -> new HashSet<>());
    }

    private static void toggle(Set<Integer> selected, int idx) {
        if (selected.contains(idx)) selected.remove(idx);
        else selected.add(idx);
    }

    // ==================== WORLDS ====================

    private static void setupWorldScreen(SelectWorldScreen ws) {
        Set<Integer> selected = sel(ws);
        clearAdded(ws);
        WorldSelectionList listW = (WorldSelectionList) getField(wsListField, ws);
        if (listW == null) return;

        MineCleanerConfig cfg = MineCleanerConfig.get();
        EditBox search = (EditBox) getField(wsSearchField, ws);

        int checkX = -1;
        int checkY = -1;
        if (search != null) {
            checkX = search.getX() + search.getWidth() + 4;
            checkY = search.getY() + (search.getHeight() - 14) / 2;
        }

        if (cfg.enableWorlds) {
            Button vanilla = (Button) getField(wsDeleteField, ws);
            int x = vanilla != null ? vanilla.getX() : ws.width / 2 - 102;
            int y = vanilla != null ? vanilla.getY() : ws.height - 28;
            int w = vanilla != null ? vanilla.getWidth() : 100;
            int h = vanilla != null ? vanilla.getHeight() : 20;
            if (vanilla != null) {
                removeWidget(ws, vanilla);
            }

            Button mine = Button.builder(
                    MineCleanerConfig.text("minecleaner.button.delete_selected"), b -> {
                if (selected.isEmpty()) {
                    var opt = listW.getSelectedOpt();
                    if (opt.isPresent()) {
                        selected.add(listW.children().indexOf(opt.get()));
                    }
                }
                if (!selected.isEmpty()) {
                    DeletionHelper.confirmAndDeleteWorlds(ws, wsListField, selected);
                }
            }).bounds(x, y, w, h).build();
            addWidget(ws, mine);
            deleteButtons.put(ws, mine);
            try { wsDeleteField.set(ws, mine); } catch (Exception e) {}

            if (search != null) {
                Checkbox all = Checkbox.builder(MineCleanerConfig.text("minecleaner.button.select_all"), Minecraft.getInstance().font)
                        .pos(checkX, checkY)
                        .onValueChange((cb, val) -> {
                            selected.clear();
                            if (val) {
                                var entries = listW.children();
                                for (int i = 0; i < entries.size(); i++) {
                                    if (entries.get(i) instanceof WorldSelectionList.WorldListEntry) selected.add(i);
                                }
                            }
                        }).build();
                addWidget(ws, all);
                checkX = all.getX() + all.getWidth() + 4;
            }

            if (eventsRegistered.add(ws)) {
                ScreenEvents.afterExtract(ws).register((s, g, mx, my, delta) -> {
                    Button b = deleteButtons.get(ws);
                    if (b != null && !b.active) b.active = true;
                    maybeSortWorlds(ws, listW, selected);
                    requestWorldSizes(listW);
                    drawWorldSizes(g, listW);
                    updateWorldDeleteButton(ws, selected);
                    drawListCheckboxes(g, mx, my, delta, ws, wsListField, WorldSelectionList.WorldListEntry.class, selected, o -> true);
                });
                ScreenMouseEvents.allowMouseClick(ws).register((s, event) ->
                        !handleListClick(event, ws, wsListField, WorldSelectionList.WorldListEntry.class, o -> true, selected,
                                event.hasShiftDown(), event.hasControlDown()));
                ScreenKeyboardEvents.allowKeyPress(ws).register((s, event) ->
                        !handleSelectAllKey(event, s, wsListField, WorldSelectionList.WorldListEntry.class, o -> true, selected));
            }
        }

        if (search != null && checkX >= 0) {
            int btnY = search.getY() + (search.getHeight() - 20) / 2;
            addWidget(ws, Button.builder(
                    MineCleanerConfig.text("minecleaner.settings.open"), b ->
                            Minecraft.getInstance().setScreenAndShow(new MineCleanerSettingsScreen(
                                    Minecraft.getInstance().gui.screen()))
            ).bounds(checkX, btnY, 60, 20).build());
        }
    }

    // ==================== SERVERS ====================

    private static void setupServerScreen(JoinMultiplayerScreen js) {
        if (!MineCleanerConfig.get().enableServers) return;
        Set<Integer> selected = sel(js);
        clearAdded(js);
        ServerSelectionList listS = (ServerSelectionList) getField(jsListField, js);
        if (listS == null) return;

        Button vanilla = (Button) getField(jsDeleteField, js);
        int x = vanilla != null ? vanilla.getX() : js.width / 2 - 102;
        int y = vanilla != null ? vanilla.getY() : js.height - 28;
        int w = vanilla != null ? vanilla.getWidth() : 100;
        int h = vanilla != null ? vanilla.getHeight() : 20;
        if (vanilla != null) {
            removeWidget(js, vanilla);
        }

        Button mine = Button.builder(
                MineCleanerConfig.text("minecleaner.button.delete_selected"), b -> {
            if (!selected.isEmpty()) {
                DeletionHelper.confirmAndDeleteServers(js, selected);
            }
        }).bounds(x, y, w, h).build();
        addWidget(js, mine);
        deleteButtons.put(js, mine);
        try { jsDeleteField.set(js, mine); } catch (Exception e) {}

        if (eventsRegistered.add(js)) {
            ScreenEvents.afterExtract(js).register((s, g, mx, my, delta) -> {
                Button b = deleteButtons.get(js);
                if (b != null && !b.active) b.active = true;
                updateCountDeleteButton(js, selected);
                drawListCheckboxes(g, mx, my, delta, js, jsListField, ServerSelectionList.OnlineServerEntry.class, selected, o -> true);
            });
            ScreenMouseEvents.allowMouseClick(js).register((s, event) ->
                    !handleListClick(event, js, jsListField, ServerSelectionList.OnlineServerEntry.class, o -> true, selected,
                            event.hasShiftDown(), event.hasControlDown()));
            ScreenKeyboardEvents.allowKeyPress(js).register((s, event) ->
                    !handleSelectAllKey(event, s, jsListField, ServerSelectionList.OnlineServerEntry.class, o -> true, selected));
        }
    }

    // ==================== PACKS ====================

    private static void setupPackScreen(PackSelectionScreen ps) {
        if (!MineCleanerConfig.get().enablePacks) return;
        Set<Integer> selected = sel(ps);
        clearAdded(ps);
        TransferableSelectionList listP = (TransferableSelectionList) getField(psAvailableField, ps);
        if (listP == null) return;

        addWidget(ps, Button.builder(
                MineCleanerConfig.text("minecleaner.button.delete_selected"), btn -> {
            if (!selected.isEmpty()) {
                DeletionHelper.confirmAndDeletePacks(ps, psAvailableField, selected);
            }
        }).bounds(ps.width / 2 - 115, ps.height - 48, 230, 20).build());

        if (eventsRegistered.add(ps)) {
            ScreenEvents.afterExtract(ps).register((s, g, mx, my, delta) -> {
                requestPackSizes(listP);
                drawPackSizes(g, listP);
                updatePackDeleteButton(ps, selected);
                drawListCheckboxes(g, mx, my, delta, ps, psAvailableField, TransferableSelectionList.PackEntry.class, selected, o -> !isBuiltinPack((TransferableSelectionList.PackEntry) o));
            });
            ScreenMouseEvents.allowMouseClick(ps).register((s, event) ->
                    !handleListClick(event, ps, psAvailableField, TransferableSelectionList.PackEntry.class, o -> !isBuiltinPack((TransferableSelectionList.PackEntry) o), selected,
                            event.hasShiftDown(), event.hasControlDown()));
            ScreenKeyboardEvents.allowKeyPress(ps).register((s, event) ->
                    !handleSelectAllKey(event, s, psAvailableField, TransferableSelectionList.PackEntry.class, o -> !isBuiltinPack((TransferableSelectionList.PackEntry) o), selected));
        }
    }

    private static boolean isBuiltinPack(TransferableSelectionList.PackEntry pe) {
        try {
            String packId = pe.getPackId();
            if (packId != null && (packId.startsWith("vanilla") || packId.startsWith("minecraft") || packId.startsWith("builtins"))) {
                return true;
            }
        } catch (Throwable e) {}
        return false;
    }

    // ==================== SHADERS ====================

    private static void setupShaderScreen(Screen screen) {
        try {
            if (!MineCleanerConfig.get().enableShaders) return;
            Set<Integer> selected = sel(screen);
            clearAdded(screen);
            Object shaderList = getField(shaderListField, screen);
            if (shaderList == null) return;

            addWidget(screen, Button.builder(
                    MineCleanerConfig.text("minecleaner.button.delete_selected"), btn -> {
                if (!selected.isEmpty()) {
                    DeletionHelper.confirmAndDeleteShaders(screen, shaderList, selected, shaderPackEntryClass);
                }
            }).bounds(screen.width / 2 - 100, 22, 200, 20).build());

            if (eventsRegistered.add(screen)) {
                ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
                    requestShaderSizes(shaderList);
                    drawShaderSizes(g, screen, shaderList, selected);
                    updateShaderDeleteButton(screen, shaderList, selected);
                    drawShaderCheckboxes(g, mx, my, delta, screen, shaderList, selected);
                });
                ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
                        !handleShaderClick(event, screen, shaderList, shaderPackEntryClass, selected,
                                event.hasShiftDown(), event.hasControlDown()));
                ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) ->
                        !handleShaderSelectAllKey(event, s, shaderList, selected));
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void drawShaderCheckboxes(GuiGraphicsExtractor g, int mx, int my, float delta,
            Screen screen, Object shaderList, Set<Integer> selected) {
        try {
            if (Minecraft.getInstance() == null) return;
            Font font = Minecraft.getInstance().font;
            @SuppressWarnings("unchecked")
            java.util.List<Object> entries = (java.util.List<Object>) shaderList.getClass().getMethod("children").invoke(shaderList);
            Method getRowTop = shaderList.getClass().getMethod("getRowTop", int.class);
            Method getRowBottom = shaderList.getClass().getMethod("getRowBottom", int.class);
            Method getRowLeft = shaderList.getClass().getMethod("getRowLeft");
            int left = (int) getRowLeft.invoke(shaderList);
            for (int i = 0; i < entries.size(); i++) {
                if (!shaderPackEntryClass.isInstance(entries.get(i))) continue;
                drawOneCheckbox(g, mx, my, delta, screen.height,
                        (int) getRowTop.invoke(shaderList, i), (int) getRowBottom.invoke(shaderList, i), left, i, selected, font);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static boolean handleShaderClick(MouseButtonEvent event, Screen screen, Object shaderList,
            Class<?> entryClass, Set<Integer> selected, boolean shift, boolean ctrl) {
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Object> entries = (java.util.List<Object>) shaderList.getClass().getMethod("children").invoke(shaderList);
            Method getRowTop = shaderList.getClass().getMethod("getRowTop", int.class);
            Method getRowBottom = shaderList.getClass().getMethod("getRowBottom", int.class);
            Method getRowLeft = shaderList.getClass().getMethod("getRowLeft");
            int left = (int) getRowLeft.invoke(shaderList);
            for (int i = 0; i < entries.size(); i++) {
                if (!entryClass.isInstance(entries.get(i))) continue;
                int rowTop = (int) getRowTop.invoke(shaderList, i);
                int rowBot = (int) getRowBottom.invoke(shaderList, i);
                int cbX = left - 16;
                if (event.x() >= cbX && event.x() <= cbX + 14 && event.y() >= rowTop && event.y() <= rowBot) {
                    toggleWithAnchor(screen, selected, i, shift, ctrl, idx -> entryClass.isInstance(entries.get(idx)));
                    return true;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    // ==================== COMMON ====================

    private static void drawListCheckboxes(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta,
            Screen screen, Field listField, Class<?> targetClass, Set<Integer> selected,
            java.util.function.Predicate<Object> filter) {
        net.minecraft.client.gui.components.AbstractSelectionList<?> list =
                (net.minecraft.client.gui.components.AbstractSelectionList<?>) getField(listField, screen);
        if (list == null || Minecraft.getInstance() == null) return;
        Font font = Minecraft.getInstance().font;
        var entries = list.children();
        for (int i = 0; i < entries.size(); i++) {
            Object e = entries.get(i);
            if (!targetClass.isInstance(e)) continue;
            if (!filter.test(e)) continue;
            drawOneCheckbox(g, mouseX, mouseY, delta, screen.height, list.getRowTop(i), list.getRowBottom(i), list.getRowLeft(), i, selected, font);
        }
    }

    private static boolean handleListClick(MouseButtonEvent event, Screen screen, Field listField,
            Class<?> targetClass, java.util.function.Predicate<Object> filter, Set<Integer> selected,
            boolean shift, boolean ctrl) {
        net.minecraft.client.gui.components.AbstractSelectionList<?> list =
                (net.minecraft.client.gui.components.AbstractSelectionList<?>) getField(listField, screen);
        if (list == null) return false;
        var entries = list.children();
        for (int i = 0; i < entries.size(); i++) {
            Object e = entries.get(i);
            if (!targetClass.isInstance(e)) continue;
            if (!filter.test(e)) continue;
            int rowTop = list.getRowTop(i);
            int rowBot = list.getRowBottom(i);
            int cbX = list.getRowLeft() - 16;
            if (event.x() >= cbX && event.x() <= cbX + 14 && event.y() >= rowTop && event.y() <= rowBot) {
                toggleWithAnchor(screen, selected, i, shift, ctrl, idx -> {
                    Object ee = entries.get(idx);
                    return targetClass.isInstance(ee) && filter.test(ee);
                });
                return true;
            }
        }
        return false;
    }

    private static void toggleWithAnchor(Screen screen, Set<Integer> selected, int idx,
            boolean shift, boolean ctrl, java.util.function.IntPredicate pass) {
        Integer anchor = rangeAnchor.get(screen);
        if (shift && anchor != null) {
            int a = Math.min(anchor, idx);
            int b = Math.max(anchor, idx);
            for (int i = a; i <= b; i++) {
                if (pass.test(i)) selected.add(i);
            }
        } else {
            toggle(selected, idx);
        }
        rangeAnchor.put(screen, idx);
    }

    private static boolean handleSelectAllKey(net.minecraft.client.input.KeyEvent event, Screen screen,
            Field listField, Class<?> targetClass, java.util.function.Predicate<Object> filter, Set<Integer> selected) {
        if (!event.isSelectAll() || screen.getFocused() instanceof EditBox) return false;
        net.minecraft.client.gui.components.AbstractSelectionList<?> list =
                (net.minecraft.client.gui.components.AbstractSelectionList<?>) getField(listField, screen);
        if (list == null) return false;
        var entries = list.children();
        selected.clear();
        for (int i = 0; i < entries.size(); i++) {
            Object e = entries.get(i);
            if (targetClass.isInstance(e) && filter.test(e)) selected.add(i);
        }
        return true;
    }

    private static boolean handleShaderSelectAllKey(net.minecraft.client.input.KeyEvent event, Screen screen,
            Object shaderList, Set<Integer> selected) {
        if (!event.isSelectAll() || screen.getFocused() instanceof EditBox) return false;
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Object> entries = (java.util.List<Object>) shaderList.getClass().getMethod("children").invoke(shaderList);
            selected.clear();
            for (int i = 0; i < entries.size(); i++) {
                if (shaderPackEntryClass.isInstance(entries.get(i))) selected.add(i);
            }
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    // ==================== WORLD SORTING ====================

    private static final java.util.Comparator<Object> WORLD_BY_NAME = (a, b) -> {
        LevelSummary sa = ((WorldSelectionList.WorldListEntry) a).getLevelSummary();
        LevelSummary sb = ((WorldSelectionList.WorldListEntry) b).getLevelSummary();
        int c = sa.getLevelName().compareToIgnoreCase(sb.getLevelName());
        if (c != 0) return c;
        return sa.getLevelId().compareTo(sb.getLevelId());
    };

    private static final java.util.Comparator<Object> WORLD_BY_DATE = java.util.Comparator
            .comparingLong((Object e) -> ((WorldSelectionList.WorldListEntry) e).getLevelSummary().getLastPlayed())
            .reversed();

    private static void maybeSortWorlds(SelectWorldScreen ws, WorldSelectionList listW, Set<Integer> selected) {
        try {
            var entries = listW.children();
            java.util.List<Object> worlds = new ArrayList<>();
            for (Object e : entries) {
                if (e instanceof WorldSelectionList.WorldListEntry) worlds.add(e);
            }
            if (worlds.size() < 2) return;
            MineCleanerConfig cfg = MineCleanerConfig.get();
            Object first = worlds.get(0);
            String fp = cfg.worldSort + "|" + worlds.size() + "|" + System.identityHashCode(first);
            if (fp.equals(sortFingerprints.get(ws))) return;
            sortFingerprints.put(ws, fp);
            reorderWorldEntries(entries, selected, cfg, worlds);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void reorderWorldEntries(java.util.List<WorldSelectionList.Entry> entries, Set<Integer> selected,
            MineCleanerConfig cfg, java.util.List<Object> worlds) {
        java.util.Map<String, Boolean> wasSelected = new HashMap<>();
        int wi = 0;
        for (int i = 0; i < entries.size(); i++) {
            Object e = entries.get(i);
            if (e instanceof WorldSelectionList.WorldListEntry) {
                wasSelected.put(((WorldSelectionList.WorldListEntry) e).getLevelSummary().getLevelId(), selected.contains(i));
                worlds.set(wi++, e);
            }
        }
        worlds.sort("name".equals(cfg.worldSort) ? WORLD_BY_NAME : WORLD_BY_DATE);
        wi = 0;
        for (int i = 0; i < entries.size(); i++) {
            Object e = entries.get(i);
            if (e instanceof WorldSelectionList.WorldListEntry) {
                entries.set(i, (WorldSelectionList.Entry) worlds.get(wi++));
            }
        }
        selected.clear();
        for (int i = 0; i < entries.size(); i++) {
            Object e = entries.get(i);
            if (e instanceof WorldSelectionList.WorldListEntry) {
                if (Boolean.TRUE.equals(wasSelected.get(((WorldSelectionList.WorldListEntry) e).getLevelSummary().getLevelId()))) {
                    selected.add(i);
                }
            }
        }
    }

    // ==================== CONTENT SIZES ====================

    private static Path contentPath(String type, String id) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameDirectory == null) return null;
            Path base = mc.gameDirectory.toPath();
            if ("world".equals(type)) return base.resolve("saves").resolve(id);
            if ("pack".equals(type)) {
                Path p = base.resolve("resourcepacks").resolve(id);
                if (Files.exists(p)) return p;
                Path z = base.resolve("resourcepacks").resolve(id + ".zip");
                return Files.exists(z) ? z : null;
            }
            if ("shader".equals(type)) return base.resolve("shaderpacks").resolve(id);
        } catch (Throwable e) {
            return null;
        }
        return null;
    }

    private static void requestSize(String key, Path path) {
        if (path == null) return;
        if (sizeCache.containsKey(key)) return;
        if (!sizePending.add(key)) return;
        SIZE_EXECUTOR.submit(() -> {
            long sz = folderSize(path);
            sizeCache.put(key, sz);
            sizePending.remove(key);
        });
    }

    private static long folderSize(Path path) {
        try {
            if (!Files.exists(path)) return -1;
            if (Files.isRegularFile(path)) return Files.size(path);
            long[] total = {0};
            Files.walk(path).forEach(p -> {
                try {
                    if (Files.isRegularFile(p)) total[0] += Files.size(p);
                } catch (Exception ignored) {
                }
            });
            return total[0];
        } catch (Exception e) {
            return -1;
        }
    }

    private static String getSizeText(String key) {
        Long sz = sizeCache.get(key);
        return sz == null ? null : formatBytes(sz);
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return null;
        double v = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int i = 0;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        if (i == 0) return bytes + " B";
        return String.format(java.util.Locale.ROOT, "%.1f %s", v, units[i]);
    }

    private static String worldId(Object entry) {
        try {
            return ((WorldSelectionList.WorldListEntry) entry).getLevelSummary().getLevelId();
        } catch (Throwable e) {
            return null;
        }
    }

    private static void requestWorldSizes(WorldSelectionList list) {
        try {
            for (Object e : list.children()) {
                if (e instanceof WorldSelectionList.WorldListEntry) {
                    String id = worldId(e);
                    if (id == null) continue;
                    requestSize("world|" + id, contentPath("world", id));
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void drawWorldSizes(GuiGraphicsExtractor g, WorldSelectionList list) {
        if (Minecraft.getInstance() == null) return;
        Font font = Minecraft.getInstance().font;
        try {
            var entries = list.children();
            int right = list.getRowLeft() + list.getRowWidth();
            for (int i = 0; i < entries.size(); i++) {
                Object e = entries.get(i);
                if (!(e instanceof WorldSelectionList.WorldListEntry)) continue;
                String id = worldId(e);
                if (id == null) continue;
                String size = getSizeText("world|" + id);
                if (size == null) continue;
                int rowTop = list.getRowTop(i);
                int rowBot = list.getRowBottom(i);
                if (rowBot < 0 || rowTop > g.guiHeight()) continue;
                StringWidget name = getWorldNameWidget(e);
                int y;
                if (name != null) {
                    name.setMaxWidth(list.getRowWidth() - 70);
                    y = name.getY();
                } else {
                    y = rowTop + (rowBot - rowTop - font.lineHeight) / 2;
                }
                g.text(font, size, right - font.width(size) - 4, y, 0xFFA0A0A0);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static StringWidget getWorldNameWidget(Object entry) {
        try {
            if (wsNameField == null) {
                wsNameField = WorldSelectionList.WorldListEntry.class.getDeclaredField("worldNameText");
                wsNameField.setAccessible(true);
            }
            Object v = wsNameField.get(entry);
            return v instanceof StringWidget ? (StringWidget) v : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static void updateWorldDeleteButton(SelectWorldScreen ws, Set<Integer> selected) {
        Button b = deleteButtons.get(ws);
        if (b == null) return;
        StringBuilder sb = new StringBuilder(MineCleanerConfig.text("minecleaner.button.delete_selected").getString());
        if (!selected.isEmpty()) {
            List<String> ids = new ArrayList<>();
            WorldSelectionList listW = (WorldSelectionList) getField(wsListField, ws);
            if (listW != null) {
                var entries = listW.children();
                for (Integer idx : selected) {
                    if (idx < 0 || idx >= entries.size()) continue;
                    Object e = entries.get(idx);
                    if (e instanceof WorldSelectionList.WorldListEntry) {
                        String id = worldId(e);
                        if (id != null) ids.add(id);
                    }
                }
            }
            long known = 0;
            int unknown = 0;
            for (String id : ids) {
                Long sz = sizeCache.get("world|" + id);
                if (sz != null && sz > 0) known += sz;
                else unknown++;
            }
            sb.append(" (").append(ids.size());
            if (unknown == 0 && known > 0) {
                String size = formatBytes(known);
                if (size != null) sb.append(", ").append(size);
            }
            sb.append(")");
        }
        setButtonIfChanged(ws, b, sb.toString());
    }

    private static String packId(Object entry) {
        try {
            return ((TransferableSelectionList.PackEntry) entry).getPackId();
        } catch (Throwable e) {
            return null;
        }
    }

    private static void requestPackSizes(TransferableSelectionList list) {
        try {
            for (Object e : list.children()) {
                if (!(e instanceof TransferableSelectionList.PackEntry)) continue;
                if (isBuiltinPack((TransferableSelectionList.PackEntry) e)) continue;
                String id = packId(e);
                if (id == null || id.contains("/")) continue;
                requestSize("pack|" + id, contentPath("pack", id));
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void drawPackSizes(GuiGraphicsExtractor g, TransferableSelectionList list) {
        if (Minecraft.getInstance() == null) return;
        Font font = Minecraft.getInstance().font;
        try {
            var entries = list.children();
            int right = list.getRowLeft() + list.getRowWidth();
            for (int i = 0; i < entries.size(); i++) {
                Object e = entries.get(i);
                if (!(e instanceof TransferableSelectionList.PackEntry)) continue;
                if (isBuiltinPack((TransferableSelectionList.PackEntry) e)) continue;
                String id = packId(e);
                if (id == null) continue;
                String size = getSizeText("pack|" + id);
                if (size == null) continue;
                int rowTop = list.getRowTop(i);
                int rowBot = list.getRowBottom(i);
                if (rowBot < 0 || rowTop > g.guiHeight()) continue;
                int y = rowTop + (rowBot - rowTop - font.lineHeight) / 2;
                g.text(font, size, right - font.width(size) - 4, y, 0xFFA0A0A0);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void updatePackDeleteButton(PackSelectionScreen ps, Set<Integer> selected) {
        Button b = deleteButtons.get(ps);
        if (b == null) return;
        StringBuilder sb = new StringBuilder(MineCleanerConfig.text("minecleaner.button.delete_selected").getString());
        if (!selected.isEmpty()) {
            List<String> ids = new ArrayList<>();
            TransferableSelectionList listP = (TransferableSelectionList) getField(psAvailableField, ps);
            if (listP != null) {
                var entries = listP.children();
                for (Integer idx : selected) {
                    if (idx < 0 || idx >= entries.size()) continue;
                    Object e = entries.get(idx);
                    if (e instanceof TransferableSelectionList.PackEntry && !isBuiltinPack((TransferableSelectionList.PackEntry) e)) {
                        String id = packId(e);
                        if (id != null) ids.add(id);
                    }
                }
            }
            long known = 0;
            int unknown = 0;
            for (String id : ids) {
                Long sz = sizeCache.get("pack|" + id);
                if (sz != null && sz > 0) known += sz;
                else unknown++;
            }
            sb.append(" (").append(ids.size());
            if (unknown == 0 && known > 0) {
                String size = formatBytes(known);
                if (size != null) sb.append(", ").append(size);
            }
            sb.append(")");
        }
        setButtonIfChanged(ps, b, sb.toString());
    }

    private static void updateCountDeleteButton(Screen screen, Set<Integer> selected) {
        Button b = deleteButtons.get(screen);
        if (b == null) return;
        String text = MineCleanerConfig.text("minecleaner.button.delete_selected").getString();
        if (!selected.isEmpty()) text += " (" + selected.size() + ")";
        setButtonIfChanged(screen, b, text);
    }

    private static void setButtonIfChanged(Screen screen, Button b, String text) {
        if (text.equals(buttonMsgState.get(screen))) return;
        buttonMsgState.put(screen, text);
        b.setMessage(Component.literal(text));
    }

    private static String shaderName(Object entry) {
        try {
            try {
                Object v = entry.getClass().getMethod("getName").invoke(entry);
                if (v instanceof String) return (String) v;
            } catch (Throwable ignored) {
            }
            Object v = entry.getClass().getMethod("getDisplayName").invoke(entry);
            if (v instanceof String) return (String) v;
            return null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static void requestShaderSizes(Object shaderList) {
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Object> entries = (java.util.List<Object>) shaderList.getClass().getMethod("children").invoke(shaderList);
            for (Object e : entries) {
                if (!shaderPackEntryClass.isInstance(e)) continue;
                String name = shaderName(e);
                if (name == null) continue;
                requestSize("shader|" + name, contentPath("shader", name));
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void drawShaderSizes(GuiGraphicsExtractor g, Screen screen, Object shaderList, Set<Integer> selected) {
        if (Minecraft.getInstance() == null) return;
        Font font = Minecraft.getInstance().font;
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Object> entries = (java.util.List<Object>) shaderList.getClass().getMethod("children").invoke(shaderList);
            Method getRowTop = shaderList.getClass().getMethod("getRowTop", int.class);
            Method getRowBottom = shaderList.getClass().getMethod("getRowBottom", int.class);
            Method getRowLeft = shaderList.getClass().getMethod("getRowLeft");
            Method getRowWidth = shaderList.getClass().getMethod("getRowWidth");
            int left = (int) getRowLeft.invoke(shaderList);
            int widthR = (int) getRowWidth.invoke(shaderList);
            int right = left + widthR;
            for (int i = 0; i < entries.size(); i++) {
                Object e = entries.get(i);
                if (!shaderPackEntryClass.isInstance(e)) continue;
                String name = shaderName(e);
                if (name == null) continue;
                String size = getSizeText("shader|" + name);
                if (size == null) continue;
                int rowTop = (int) getRowTop.invoke(shaderList, i);
                int rowBot = (int) getRowBottom.invoke(shaderList, i);
                if (rowBot < 0 || rowTop > g.guiHeight()) continue;
                int y = rowTop + (rowBot - rowTop - font.lineHeight) / 2;
                g.text(font, size, right - font.width(size) - 4, y, 0xFFA0A0A0);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void updateShaderDeleteButton(Screen screen, Object shaderList, Set<Integer> selected) {
        Button b = deleteButtons.get(screen);
        if (b == null) return;
        StringBuilder sb = new StringBuilder(MineCleanerConfig.text("minecleaner.button.delete_selected").getString());
        if (!selected.isEmpty()) {
            List<String> names = new ArrayList<>();
            try {
                @SuppressWarnings("unchecked")
                java.util.List<Object> entries = (java.util.List<Object>) shaderList.getClass().getMethod("children").invoke(shaderList);
                for (Integer idx : selected) {
                    if (idx < 0 || idx >= entries.size()) continue;
                    Object e = entries.get(idx);
                    if (shaderPackEntryClass.isInstance(e)) {
                        String name = shaderName(e);
                        if (name != null) names.add(name);
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            long known = 0;
            int unknown = 0;
            for (String name : names) {
                Long sz = sizeCache.get("shader|" + name);
                if (sz != null && sz > 0) known += sz;
                else unknown++;
            }
            sb.append(" (").append(names.size());
            if (unknown == 0 && known > 0) {
                String size = formatBytes(known);
                if (size != null) sb.append(", ").append(size);
            }
            sb.append(")");
        }
        setButtonIfChanged(screen, b, sb.toString());
    }

    private static void drawOneCheckbox(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, int screenH,
                                  int rowTop, int rowBot, int rowLeft, int index, Set<Integer> selected, Font font) {
        if (rowBot < 0 || rowTop > screenH) return;
        int cbX = rowLeft - 16;
        int cbY = rowTop + 1;
        boolean checked = selected.contains(index);
        Checkbox temp = Checkbox.builder(Component.literal(""), font)
                .pos(cbX, cbY).selected(checked).build();
        temp.extractContents(g, mouseX, mouseY, delta);
    }
}
