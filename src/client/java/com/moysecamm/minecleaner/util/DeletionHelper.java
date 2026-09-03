package com.moysecamm.minecleaner.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

public class DeletionHelper {

    // ==================== WORLDS ====================

    public static void confirmAndDeleteWorlds(SelectWorldScreen screen,
                                                Field listField,
                                                Set<Integer> selectedIndices) {
        if (selectedIndices.isEmpty()) return;
        int count = selectedIndices.size();
        Minecraft.getInstance().setScreenAndShow(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        WorldSelectionList list = listOf(listField, screen);
                        if (list != null) doDeleteWorlds(list, selectedIndices);
                    }
                    Minecraft.getInstance().setScreenAndShow(screen);
                },
                Component.literal("\u0423\u0434\u0430\u043b\u0438\u0442\u044c \u043c\u0438\u0440\u044b"),
                Component.literal("\u0412\u044b \u0443\u0432\u0435\u0440\u0435\u043d\u044b, \u0447\u0442\u043e \u0445\u043e\u0442\u0438\u0442\u0435 \u0443\u0434\u0430\u043b\u0438\u0442\u044c " + count
                        + " \u043c\u0438\u0440(\u0430)? \u042d\u0442\u043e \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435 \u043d\u0435\u043e\u0431\u0440\u0430\u0442\u0438\u043c\u043e!"),
                Component.literal("\u0414\u0430"),
                Component.literal("\u041d\u0435\u0442")
        ));
    }

    private static WorldSelectionList listOf(Field f, Object target) {
        try {
            return (WorldSelectionList) f.get(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static void doDeleteWorlds(WorldSelectionList list, Set<Integer> selectedIndices) {
        var entries = list.children();
        var toDelete = selectedIndices.stream()
                .sorted(Comparator.reverseOrder())
                .map(i -> (i >= 0 && i < entries.size()) ? entries.get(i) : null)
                .filter(e -> e instanceof WorldSelectionList.WorldListEntry)
                .map(e -> (WorldSelectionList.WorldListEntry) e)
                .collect(Collectors.toList());

        for (var entry : toDelete) {
            try {
                String levelId = getLevelId(entry);
                if (levelId != null && !levelId.isEmpty()) {
                    Path worldPath = Minecraft.getInstance().gameDirectory.toPath()
                            .resolve("saves").resolve(levelId);
                    if (Files.exists(worldPath)) {
                        deleteDir(worldPath);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Refresh the visible world list so deleted worlds disappear immediately.
        try {
            list.reloadWorldList();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getLevelId(WorldSelectionList.WorldListEntry entry) {
        try {
            for (Field field : WorldSelectionList.WorldListEntry.class.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(entry);
                if (value == null) continue;
                try {
                    Method getLevelId = value.getClass().getMethod("getLevelId");
                    Object result = getLevelId.invoke(value);
                    if (result instanceof String s && !s.isEmpty()) {
                        return s;
                    }
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception ignored) {}

        try {
            Method m = WorldSelectionList.WorldListEntry.class.getMethod("getLevelName");
            Object result = m.invoke(entry);
            if (result instanceof String s && !s.isEmpty()) {
                return s;
            }
        } catch (Exception ignored) {}

        return null;
    }

    // ==================== SERVERS ====================

    public static void confirmAndDeleteServers(
            net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen screen,
            Set<Integer> selectedIndices) {
        if (selectedIndices.isEmpty()) return;
        int count = selectedIndices.size();
        Minecraft.getInstance().setScreenAndShow(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        doDeleteServers(screen, selectedIndices);
                    }
                    Minecraft.getInstance().setScreenAndShow(screen);
                },
                Component.literal("\u0423\u0434\u0430\u043b\u0438\u0442\u044c \u0441\u0435\u0440\u0432\u0435\u0440\u044b"),
                Component.literal("\u0412\u044b \u0443\u0432\u0435\u0440\u0435\u043d\u044b, \u0447\u0442\u043e \u0445\u043e\u0442\u0438\u0442\u0435 \u0443\u0434\u0430\u043b\u0438\u0442\u044c " + count
                        + " \u0441\u0435\u0440\u0432\u0435\u0440(\u0430)? \u042d\u0442\u043e \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435 \u043d\u0435\u043e\u0431\u0440\u0430\u0442\u0438\u043c\u043e!"),
                Component.literal("\u0414\u0430"),
                Component.literal("\u041d\u0435\u0442")
        ));
    }

    private static void doDeleteServers(
            net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen screen,
            Set<Integer> selectedIndices) {
        try {
            ServerList serverList = screen.getServers();
            if (serverList == null) return;

            net.minecraft.client.gui.screens.multiplayer.ServerSelectionList list =
                    (net.minecraft.client.gui.screens.multiplayer.ServerSelectionList)
                            serverSelectionListField(screen);
            var entries = list == null ? java.util.List.<Object>of() : list.children();

            Class<?> onlineEntryClass =
                    net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.OnlineServerEntry.class;
            Method getServerData = onlineEntryClass.getMethod("getServerData");

            var dataToRemove = new java.util.ArrayList<net.minecraft.client.multiplayer.ServerData>();
            for (int idx : selectedIndices) {
                if (idx < 0 || idx >= entries.size()) continue;
                Object e = entries.get(idx);
                if (!onlineEntryClass.isInstance(e)) continue;
                try {
                    Object data = getServerData.invoke(e);
                    if (data instanceof net.minecraft.client.multiplayer.ServerData sd) {
                        dataToRemove.add(sd);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            for (var data : dataToRemove) {
                for (int i = 0; i < serverList.size(); i++) {
                    if (serverList.get(i) == data) {
                        serverList.remove(data);
                        break;
                    }
                }
            }
            serverList.save();

            // Refresh visible list so deleted servers disappear immediately.
            if (list != null) {
                list.setSelected(null);
                list.updateOnlineServers(serverList);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Object serverSelectionListField(Object screen) {
        try {
            Field f = net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen.class
                    .getDeclaredField("serverSelectionList");
            f.setAccessible(true);
            return f.get(screen);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== PACKS ====================

    public static void confirmAndDeletePacks(
            PackSelectionScreen screen,
            Field listField,
            Set<Integer> selectedIndices) {
        if (selectedIndices.isEmpty()) return;
        int count = selectedIndices.size();
        Minecraft.getInstance().setScreenAndShow(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        try {
                            TransferableSelectionList list = (TransferableSelectionList) listField.get(screen);
                            if (list != null) doDeletePacks(screen, list, selectedIndices);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    Minecraft.getInstance().setScreenAndShow(screen);
                },
                Component.literal("\u0423\u0434\u0430\u043b\u0438\u0442\u044c \u043f\u0430\u043a\u0438"),
                Component.literal("\u0412\u044b \u0443\u0432\u0435\u0440\u0435\u043d\u044b, \u0447\u0442\u043e \u0445\u043e\u0442\u0438\u0442\u0435 \u0443\u0434\u0430\u043b\u0438\u0442\u044c " + count
                        + " \u043f\u0430\u043a(\u043e\u0432)? \u042d\u0442\u043e \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435 \u043d\u0435\u043e\u0431\u0440\u0430\u0442\u0438\u043c\u043e!"),
                Component.literal("\u0414\u0430"),
                Component.literal("\u041d\u0435\u0442")
        ));
    }

    private static void doDeletePacks(PackSelectionScreen screen, TransferableSelectionList list, Set<Integer> selectedIndices) {
        Path rpDir = Minecraft.getInstance().gameDirectory.toPath().resolve("resourcepacks");
        var entries = list.children();
        var toDelete = selectedIndices.stream()
                .sorted(Comparator.reverseOrder())
                .map(i -> (i >= 0 && i < entries.size()) ? entries.get(i) : null)
                .filter(e -> e instanceof TransferableSelectionList.PackEntry)
                .map(e -> (TransferableSelectionList.PackEntry) e)
                .collect(Collectors.toList());

        for (var entry : toDelete) {
            try {
                String packId = getPackId(entry);
                if (packId == null || packId.isEmpty()) continue;
                if (packId.startsWith("vanilla") || packId.startsWith("minecraft") || packId.startsWith("builtins") || packId.contains("/")) continue;

                Path p = rpDir.resolve(packId);
                if (Files.exists(p)) {
                    if (Files.isDirectory(p)) deleteDir(p);
                    else Files.deleteIfExists(p);
                } else {
                    Path pz = rpDir.resolve(packId + ".zip");
                    if (Files.exists(pz)) Files.deleteIfExists(pz);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Re-scan resource packs and refresh both lists so deleted packs disappear immediately.
        try {
            Method reload = PackSelectionScreen.class.getDeclaredMethod("reload");
            reload.setAccessible(true);
            reload.invoke(screen);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getPackId(TransferableSelectionList.PackEntry entry) {
        try {
            return entry.getPackId();
        } catch (Exception ignored) {}
        try {
            for (Field field : entry.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(entry);
                if (value instanceof String s && !s.isEmpty()) return s;
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ==================== SHADERS ====================

    public static void confirmAndDeleteShaders(Screen screen, Object shaderList,
                                               Set<Integer> selectedIndices, Class<?> shaderPackEntryClass) {
        if (selectedIndices.isEmpty()) return;
        int count = selectedIndices.size();
        Minecraft.getInstance().setScreenAndShow(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        doDeleteShaders(shaderList, selectedIndices, shaderPackEntryClass);
                    }
                    Minecraft.getInstance().setScreenAndShow(screen);
                },
                Component.literal("\u0423\u0434\u0430\u043b\u0438\u0442\u044c \u0448\u0435\u0439\u0434\u0435\u0440-\u043f\u0430\u043a\u0438"),
                Component.literal("\u0412\u044b \u0443\u0432\u0435\u0440\u0435\u043d\u044b, \u0447\u0442\u043e \u0445\u043e\u0442\u0438\u0442\u0435 \u0443\u0434\u0430\u043b\u0438\u0442\u044c " + count
                        + " \u0448\u0435\u0439\u0434\u0435\u0440-\u043f\u0430\u043a(\u0430)? \u042d\u0442\u043e \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435 \u043d\u0435\u043e\u0431\u0440\u0430\u0442\u0438\u043c\u043e!"),
                Component.literal("\u0414\u0430"),
                Component.literal("\u041d\u0435\u0442")
        ));
    }

    private static void doDeleteShaders(Object shaderList, Set<Integer> selectedIndices,
                                        Class<?> shaderPackEntryClass) {
        try {
            Path spDir = Minecraft.getInstance().gameDirectory.toPath().resolve("shaderpacks");
            Method childrenMethod = shaderList.getClass().getMethod("children");
            @SuppressWarnings("unchecked")
            java.util.List<Object> entries = (java.util.List<Object>) childrenMethod.invoke(shaderList);
            Method getPackName = shaderPackEntryClass.getMethod("getPackName");

            var toDelete = selectedIndices.stream()
                    .filter(i -> i >= 0 && i < entries.size())
                    .map(i -> entries.get(i))
                    .filter(e -> shaderPackEntryClass.isInstance(e))
                    .collect(Collectors.toList());

            for (Object entry : toDelete) {
                try {
                    String packName = (String) getPackName.invoke(entry);
                    if (packName == null || packName.isEmpty()) continue;

                    Path p = spDir.resolve(packName);
                    if (Files.isDirectory(p)) {
                        deleteDir(p);
                    } else {
                        Path zip = spDir.resolve(packName + ".zip");
                        Files.deleteIfExists(zip);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void deleteDir(Path dir) throws java.io.IOException {
        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(p -> {
            try { Files.deleteIfExists(p); } catch (java.io.IOException e) {}
        });
    }
}
