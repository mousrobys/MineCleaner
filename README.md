# MineCleaner

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/licenses/MIT)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Mod%20Loader-Fabric-dbd0b4)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange)](https://www.oracle.com/java/technologies/downloads/)
[![Release](https://img.shields.io/github/v/release/mousrobys/MineCleaner)](https://github.com/mousrobys/MineCleaner/releases)

**MineCleaner** — удобный мод для Minecraft (Fabric), который позволяет быстро выбирать и удалять миры, серверы, ресурс-паки и шейдер-паки (Iris) прямо из игрового меню с помощью галочек.

## ✨ Возможности

- ✅ **Миры** — массовый выбор и удаление миров галочками (несколько за раз)
- ✅ **Серверы** — удаление сразу нескольких серверов
- ✅ **Ресурс-паки** — удаление ненужных ресурс-паков
- ✅ **Шейдер-паки (Iris)** — удаление шейдеров
- ✅ Кнопка **«Удалить выбранные»** и кнопка **«Все»** для быстрого выбора
- ✅ Полностью на **русском языке**
- ✅ Видимые галочки у каждой строки списка

## ⚙️ Требования

- Minecraft **26.2**
- [Fabric Loader](https://fabricmc.net/use/) **0.19.3+**
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Iris](https://irisshaders.github.io/) (только для удаления шейдер-паков)
- Java **25**

## 📥 Установка

1. Скачайте последнюю версию мода в разделе [Releases](https://github.com/mousrobys/MineCleaner/releases)
2. Скопируйте файл `minecleaner-*.jar` в папку `mods` вашего экземпляра Minecraft
3. Запустите игру через Fabric
4. Откройте нужное меню (миры / серверы / ресурс-паки / шейдеры) и удаляйте с галочками

> ⚠️ **Внимание:** удаление необратимо. Файлы удаляются с диска. Будьте осторожны!

## 🖱️ Использование

| Меню | Что делает |
|------|------------|
| Выбор мира | Галочка «Все» + галочки у миров, кнопка «Удалить выбранные» |
| Серверы | Галочки у серверов, кнопка «Удалить выбранные» |
| Ресурс-паки | Галочки у пакетов, кнопка «Удалить выбранные» |
| Шейдер-паки (Iris) | Галочки у шейдеров, кнопка «Удалить выбранные» |

## 🛠️ Сборка из исходников

```bash
# Windows (PowerShell)
$env:JAVA_HOME = "путь_к_jdk25"
.\gradlew.bat build
```

Готовый jar появится в `build/libs/`.

## 🗂️ Структура проекта

```
src/
├── main/java/com/moysecamm/minecleaner/   — общий код (загрузка мода)
├── main/resources/                        — fabric.mod.json, иконка
└── client/java/com/moysecamm/minecleaner/ — клиентский код (Gui, логика)
```

## 📄 Лицензия

Проект распространяется под лицензией [MIT](LICENSE).
