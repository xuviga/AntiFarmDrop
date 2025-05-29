<h1 align="center">🧱 AntiFarmDrop</h1>
<p align="center">
  Защита от фарма мобов через падение и ловушки на Minecraft сервере.
</p>
<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.20--1.21-green?style=flat-square"/>
  <img src="https://img.shields.io/badge/Java-17+-blue?style=flat-square"/>
  <img src="https://img.shields.io/github/license/xuvigan/AntiFarmDrop?style=flat-square"/>
</p>

---

## 🚀 Возможности

- ❌ Блокирует дроп, если моб:
    - Упал с высоты (`FALL`)
    - Получил небоевой урон (лава, огонь, утопление)
- ✅ Не блокирует дроп, если игрок атаковал до падения
- 🧩 Гибкая конфигурация (`config.yml`)
- 🔄 Поддержка кастомных событий (`DropBlockedEvent`)
- 🧠 Оптимизированный трекер высоты падения
- ⚙️ Прост в установке и настройке

---

## 📦 Установка

1. Скачайте `AntiFarmDrop.jar` и поместите в `/plugins`
2. Запустите сервер
3. Настройте `config.yml` по желанию
4. (опционально) Подключитесь к API через `DropBlockedEvent`

---

## 💻 API для разработчиков

### 📘 Проверка через API:
```java
boolean allowed = AntiFarmDropPlugin.isDropAllowed(entity);
```

### 📡 Событие `DropBlockedEvent`
```java
@EventHandler
public void onDropBlocked(DropBlockedEvent event) {
    if (event.getReason() == DropBlockedEvent.BlockReason.FALL &&
        event.getEntity().getType() == EntityType.CREEPER) {
        event.setCancelled(true); // Разрешить дроп для криперов
    }
}
```

---

## ⚙️ Конфигурация (`config.yml`)

```yaml
min-fall-height: 2.0         # Минимальная высота, при которой считается "падение"
max-track-time: 15000        # Время отслеживания падения (мс)
log-blocked-drops: true      # Вывод в консоль при блокировке дропа

blocked-damage-causes:       # Причины, при которых моб считается "подозрительным"
  - FALL
  - LAVA
  - FIRE
  - FIRE_TICK
  - DROWNING
  - VOID

excluded-entities:           # Сущности, которые игнорируются системой
  - ARMOR_STAND
  - VILLAGER
```

---

## 🛡️ Совместимость

| Платформа   | Версия      |
|-------------|-------------|
| Spigot      | ✅ 1.20–1.21 |
| Paper/Purpur| ✅ Поддержка |
| Java        | ✅ 17 и выше |

---

## 📚 Лицензия

MIT License © 2025 [XuViGaN](https://github.com/XuViGaN)