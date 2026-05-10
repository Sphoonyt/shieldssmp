# ShieldsSMP – Mace Abilities Plugin
**Paper 1.21.1**

---

## Abilities

| Ability | How it triggers | Details |
|---|---|---|
| **Dash** | Right-click with mace | Launches forward in your look-direction |
| **Windburst** | Land on ground while holding mace | Radial push on all nearby entities |
| **3-Hit Launch** | 3 consecutive mace hits on the same target | Launches target ~50 blocks upward |
| **Shockwave** | Launched target lands | Explodes outward, dealing ½ max armor durability damage to nearby players |

---

## Commands

| Command | Permission | Description |
|---|---|---|
| `/mace` | `shieldssmp.mace` (default: op) | Give yourself the Shields SMP Mace |
| `/mace <player>` | `shieldssmp.mace` | Give another player the mace |
| `/macereload` | `shieldssmp.admin` | Hot-reload config.yml values |

---

## Building

Requires **Java 21** and **Maven 3.8+**.

```bash
cd ShieldsSMP
mvn clean package
```

The compiled jar will be at `target/ShieldsSMP-1.0.0.jar`.

---

## Installation

1. Drop `ShieldsSMP-1.0.0.jar` into your Paper server's `plugins/` folder.
2. Start (or restart) the server.
3. Edit `plugins/ShieldsSMP/config.yml` to tune cooldowns, forces, and damage.
4. Run `/macereload` to apply config changes without restarting.

---

## config.yml Highlights

```yaml
mace:
  dash:
    velocity: 1.6       # Dash speed
    cooldown: 5         # Seconds between dashes

  windburst:
    radius: 6.0         # Push radius (blocks)
    force: 2.8          # Push strength
    cooldown: 8         # Seconds between windbursts
    min-airborne-ticks: 10  # Min airborne time before windburst triggers

  combo:
    hits-required: 3    # Hits before launch
    reset-seconds: 5    # Combo timeout
    launch-velocity: 3.2 # ~50 blocks vertically

  shockwave:
    radius: 10.0
    armor-damage-fraction: 0.5  # 0.5 = half of max durability per piece
    allow-break: false   # true = armor can fully break
```

---

## How the 3-Hit Combo Works

1. Strike the **same entity** with the mace up to 3 times.
2. If you switch targets or wait more than 5 s, the combo resets.
3. On the 3rd hit the target is **launched straight up** (~50 blocks).
4. When the target lands, a **shockwave** fires:
   - Knocks back all nearby players radially.
   - Removes **½ max durability** from each of their worn armor pieces.

---

## Notes

- The mace is identified by a [Persistent Data Container](https://jd.papermc.io/paper/1.21.1/) tag, so renaming it won't break detection.
- Sounds use vanilla 1.21 Breeze mob sounds (`ENTITY_BREEZE_WIND_BURST`, `ENTITY_BREEZE_SHOOT`).
- Tested on Paper build **#148** (1.21.1).
