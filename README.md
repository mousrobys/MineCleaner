# MineCleaner

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/licenses/MIT)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Mod%20Loader-Fabric-dbd0b4)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange)](https://www.oracle.com/java/technologies/downloads/)
[![Release](https://img.shields.io/github/v/release/mousrobys/MineCleaner)](https://github.com/mousrobys/MineCleaner/releases)

**MineCleaner** — удобный мод для Minecraft (Fabric), который позволяет быстро выбирать и удалять миры, серверы, ресурс-паки и шейдер-паки (Iris) прямо из игрового меню с помощью галочек.

**MineCleaner** is a handy Fabric mod for Minecraft that lets you quickly select and delete worlds, servers, resource packs and shader packs (Iris) right from the in-game menus using checkboxes.

## ✨ Возможности / Features

**Русский**
- ✅ **Миры** — массовый выбор и удаление миров галочками (несколько за раз), сортировка по дате или имени
- ✅ **Серверы** — удаление сразу нескольких серверов
- ✅ **Ресурс-паки** — удаление ненужных ресурс-паков
- ✅ **Шейдер-паки (Iris)** — удаление шейдеров
- ✅ **Меню настроек** (клавиша **K** или кнопка «Настройки» на экране миров): включение/выключение галочек для каждого раздела
- ✅ **Интеграция с Mod Menu** — настройки открываются с карточки мода
- ✅ **Горячие клавиши**: **Ctrl+A** — выделить всё, **Shift+клик** — выделить диапазон
- ✅ **Размеры** — размер миров и ресурс-паков в списке + суммарный размер выбранного на кнопке удаления
- ✅ Кнопки **«Удалить выбранные»** и **«Все»**
- ✅ **Полная локализация** — автоматически меняется на язык игры (13 языков + режим «язык игры»)

**English**
- ✅ **Worlds** — bulk select and delete worlds with checkboxes, sort by date or by name
- ✅ **Servers** — delete multiple servers at once
- ✅ **Resource packs** — delete unwanted resource packs
- ✅ **Shader packs (Iris)** — delete shaders
- ✅ **Settings screen** (key **K** or the "Settings" button on the Worlds screen): toggle checkboxes per section
- ✅ **Mod Menu integration** — open settings from the mod's card in Mod Menu
- ✅ **Hotkeys**: **Ctrl+A** to select all, **Shift+click** to select a range
- ✅ **Sizes** — size shown in worlds and resource packs lists, plus total size of the selection on the delete button
- ✅ **Delete Selected** and **Select All** buttons
- ✅ **Full localization** — follows the game language (13 languages + "game language" mode)

## ⚙️ Требования / Requirements

- Minecraft **26.2**
- [Fabric Loader](https://fabricmc.net/use/) **0.19.3+**
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Mod Menu](https://modrinth.com/mod/modmenu) (необязательно / optional)
- [Iris](https://irisshaders.github.io/) (только для удаления шейдер-паков / shader deletion only)
- Java **25**

## 📥 Установка / Installation

1. Скачайте последнюю версию мода в разделе [Releases](https://github.com/mousrobys/MineCleaner/releases) / Download the latest mod version from [Releases](https://github.com/mousrobys/MineCleaner/releases)
2. Скопируйте файл `minecleaner-*.jar` в папку `mods` вашего экземпляра Minecraft / Copy `minecleaner-*.jar` into your Minecraft instance's `mods` folder
3. Запустите игру через Fabric / Launch the game with Fabric
4. Откройте нужное меню (миры / серверы / ресурс-паки / шейдеры) и удаляйте с галочками / Open the desired screen (worlds / servers / resource packs / shaders) and delete with checkboxes

> ⚠️ **Внимание / Warning:** удаление необратимо. Файлы удаляются с диска. Будьте осторожны! / Deletion is permanent — files are removed from disk. Be careful!

## 🖱️ Использование / Usage

| Меню / Screen | RU | EN |
|------|------------|------------|
| Миры / Worlds | Галочка «Все» + Ctrl+A, Shift+клик — диапазон, сортировка | Select All + Ctrl+A, Shift+click — range, sorting |
| Серверы / Servers | Галочки у серверов, Ctrl+A | Checkboxes per server, Ctrl+A |
| Ресурс-паки / Resource packs | Галочки у пакетов, Ctrl+A | Checkboxes per pack, Ctrl+A |
| Шейдер-паки / Shader packs (Iris) | Галочки у шейдеров, Ctrl+A | Checkboxes per shader, Ctrl+A |

- **Настройки / Settings**: клавиша **K** / key **K**, либо кнопка «Настройки», либо Mod Menu.
- **Сортировка миров / World sorting**: в настройках — по дате или по имени / in settings — by date or by name.

## 🛠️ Сборка из исходников / Building from source

```bash
# Windows (PowerShell)
$env:JAVA_HOME = "путь_к_jdk25" # path to JDK 25
.\gradlew.bat build
```

Готовый jar появится в `build/libs/`. / The built jar lands in `build/libs/`.

## 🗂️ Структура проекта / Project structure

```
src/
├── main/java/com/moysecamm/minecleaner/   — общий код (загрузка мода) / common code (mod loading)
├── main/resources/                        — fabric.mod.json, иконка / icon, lang files
└── client/java/com/moysecamm/minecleaner/ — клиентский код (Gui, логика) / client code (GUI, logic)
```

## 🌍 Локализация / Localization

Интерфейс мода **полностью локализован** и автоматически подстраивается под язык Minecraft (или используется «язык игры» — язык самого клиента). / The mod UI is **fully localized** and automatically follows the Minecraft language (or the "game language" — the client's own language).

- 🇷🇺 Русский (ru_ru)
- 🇬🇧 English (en_us)
- 🇩🇪 Deutsch (de_de)
- 🇫🇷 Français (fr_fr)
- 🇪🇸 Español (es_es)
- 🇮🇹 Italiano (it_it)
- 🇺🇦 Українська (uk_ua)
- 🇵🇱 Polski (pl_pl)
- 🇧🇷 Português (BR) (pt_br)
- 🇯🇵 日本語 (ja_jp)
- 🇨🇳 简体中文 (zh_cn)
- 🇰🇷 한국어 (ko_kr)
- 🇹🇷 Türkçe (tr_tr)

Если языка нет в списке, используется язык игры / английский. / If a language is missing, the game language / English is used.

## 📄 Лицензия / License

Проект распространяется под лицензией [MIT](LICENSE).