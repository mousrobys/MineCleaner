package com.moysecamm.minecleaner.client;

import com.moysecamm.minecleaner.util.DeletionHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class MineCleanerClient implements ClientModInitializer {

    private static final Map<Screen, Set<Integer>> selectedMap = new IdentityHashMap<>();
    private static final Map<Screen, List<GuiEventListener>> addedMap = new IdentityHashMap<>();
    private static final Map<Screen, Button> deleteButtons = new IdentityHashMap<>();
    private static final Set<Screen> eventsRegistered = Collections.synchronizedSet(new HashSet<>());

    private static Method addWidgetMethod;
    private static Method removeWidgetMethod;

    private static Field wsListField;
    private static Field wsDeleteField;
    private static Field wsSearchField;
    private static Field jsListField;
    private static Field jsDeleteField;
    private static Field psAvailableField;

    private static Class<?> shaderPackScreenClass;
    private static Class<?> shaderPackEntryClass;
    private static Field shaderListField;

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

        Button vanilla = (Button) getField(wsDeleteField, ws);
        int x = vanilla != null ? vanilla.getX() : ws.width / 2 - 102;
        int y = vanilla != null ? vanilla.getY() : ws.height - 28;
        int w = vanilla != null ? vanilla.getWidth() : 100;
        int h = vanilla != null ? vanilla.getHeight() : 20;
        if (vanilla != null) {
            removeWidget(ws, vanilla);
        }

        Button mine = Button.builder(
                Component.translatable("minecleaner.button.delete_selected"), b -> {
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

        EditBox search = (EditBox) getField(wsSearchField, ws);
        if (search != null) {
            addWidget(ws, Checkbox.builder(Component.translatable("minecleaner.button.select_all"), Minecraft.getInstance().font)
                    .pos(search.getX() + search.getWidth() + 4, search.getY() + (search.getHeight() - 14) / 2)
                    .onValueChange((cb, val) -> {
                        selected.clear();
                        if (val) {
                            var entries = listW.children();
                            for (int i = 0; i < entries.size(); i++) {
                                if (entries.get(i) instanceof WorldSelectionList.WorldListEntry) selected.add(i);
                            }
                        }
                    }).build());
        }

        if (eventsRegistered.add(ws)) {
            ScreenEvents.afterExtract(ws).register((s, g, mx, my, delta) -> {
                Button b = deleteButtons.get(ws);
                if (b != null && !b.active) b.active = true;
                drawListCheckboxes(g, mx, my, delta, ws, wsListField, WorldSelectionList.WorldListEntry.class, selected, o -> true);
            });
            ScreenMouseEvents.allowMouseClick(ws).register((s, event) ->
                    !handleListClick(event, ws, wsListField, WorldSelectionList.WorldListEntry.class, o -> true, selected));
        }
    }

    // ==================== SERVERS ====================

    private static void setupServerScreen(JoinMultiplayerScreen js) {
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
                Component.translatable("minecleaner.button.delete_selected"), b -> {
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
                drawListCheckboxes(g, mx, my, delta, js, jsListField, ServerSelectionList.OnlineServerEntry.class, selected, o -> true);
            });
            ScreenMouseEvents.allowMouseClick(js).register((s, event) ->
                    !handleListClick(event, js, jsListField, ServerSelectionList.OnlineServerEntry.class, o -> true, selected));
        }
    }

    // ==================== PACKS ====================

    private static void setupPackScreen(PackSelectionScreen ps) {
        Set<Integer> selected = sel(ps);
        clearAdded(ps);
        TransferableSelectionList listP = (TransferableSelectionList) getField(psAvailableField, ps);
        if (listP == null) return;

        addWidget(ps, Button.builder(
                Component.translatable("minecleaner.button.delete_selected"), btn -> {
            if (!selected.isEmpty()) {
                DeletionHelper.confirmAndDeletePacks(ps, psAvailableField, selected);
            }
        }).bounds(ps.width / 2 - 115, ps.height - 48, 230, 20).build());

        if (eventsRegistered.add(ps)) {
            ScreenEvents.afterExtract(ps).register((s, g, mx, my, delta) ->
                    drawListCheckboxes(g, mx, my, delta, ps, psAvailableField, TransferableSelectionList.PackEntry.class, selected, o -> !isBuiltinPack((TransferableSelectionList.PackEntry) o)));
            ScreenMouseEvents.allowMouseClick(ps).register((s, event) ->
                    !handleListClick(event, ps, psAvailableField, TransferableSelectionList.PackEntry.class, o -> !isBuiltinPack((TransferableSelectionList.PackEntry) o), selected));
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
            Set<Integer> selected = sel(screen);
            clearAdded(screen);
            Object shaderList = getField(shaderListField, screen);
            if (shaderList == null) return;

            addWidget(screen, Button.builder(
                    Component.translatable("minecleaner.button.delete_selected"), btn -> {
                if (!selected.isEmpty()) {
                    DeletionHelper.confirmAndDeleteShaders(screen, shaderList, selected, shaderPackEntryClass);
                }
            }).bounds(screen.width / 2 - 100, 22, 200, 20).build());

            if (eventsRegistered.add(screen)) {
                ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) ->
                        drawShaderCheckboxes(g, mx, my, delta, screen, shaderList, selected));
                ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
                        !handleShaderClick(event, shaderList, shaderPackEntryClass, selected));
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

    private static boolean handleShaderClick(MouseButtonEvent event, Object shaderList,
            Class<?> entryClass, Set<Integer> selected) {
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
                    toggle(selected, i);
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
            Class<?> targetClass, java.util.function.Predicate<Object> filter, Set<Integer> selected) {
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
                toggle(selected, i);
                return true;
            }
        }
        return false;
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
