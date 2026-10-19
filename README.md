# AoW2 Bot

An Android app with a built-in AI that plays **Age of War 2** on the same phone.
It watches the screen (screen capture), reads the game, decides, and taps the game's own buttons
(accessibility gestures). Everything runs on the phone — no internet, no cloud AI.

## What it reads
| Thing | How |
|---|---|
| Gold, army size (`7/10`) | digit reader trained on the game font (`vision/Ocr`, `vision/Templates`) |
| Base health, enemy health | green share of the two top bars |
| XP to next age | blue fill of the bar under the spell icons |
| Spell ready | glow ring around the meteor icon |
| Unit prices, unlocked units | white price text on the four unit buttons |
| Upgrade / tower prices | green buy buttons found by colour, price read inside |
| Fights near the base | ground strip compared with the ground colour, 50 columns |

## How it decides (`ai/Brain`)
Every frame, in order: tap **Ready!** → finish any purchase in progress → **evolve** when the XP bar is full →
**spell** when the base is losing health or a big fight is on → **under attack**: spend everything on units →
otherwise keep the army topped up, and once it is big enough save for the next item of the plan
(towers/turrets, income, infantry armour, …).

Three styles (`ai/Profile`): **Balanced**, **Turtle**, **Rush**. In *Auto-learn* mode the bot keeps score of
wins per style and picks the next one with UCB1 (`ai/Learner`), so it drifts towards whatever beats your difficulty.

Every unit/upgrade tap is checked: if gold doesn't drop, that action is paused for a while instead of being spammed.

## Setup on the phone
1. Install the APK from the latest GitHub release (built by Actions on every push to `main`).
2. In the app: open Accessibility settings and turn on **AoW2 Bot**; a small panel appears at the top.
3. Start screen capture (Entire screen).
4. Open Age of War 2, pick a difficulty, press **▶ Start** on the panel.

Logs: `Download/AoW2Bot/aow2_*.txt` (one JSON line per tap/event). Screenshots of menus the bot doesn't know
are saved automatically to `Pictures/AoW2Bot` (also the **Snap** button). Share these to improve the bot.

## Testing on a computer
Vision and AI have no Android dependencies:
```
javac -d build app/src/main/java/com/nickrocks/aow2bot/{vision,ai}/*.java tools/desktop/*.java
java -cp build HudProbe samples 1          # what the bot reads from each frame (rename samples to g_NNN.jpg)
java -cp build Replay <frames dir>          # what the brain would tap over a recording
```
`tools/desktop/MakeTemplates` rebuilds the digit templates from labelled frames (`tools/data`).

Screen positions were measured on a 2520×1080 phone and are scaled to other screens.
