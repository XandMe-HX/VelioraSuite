# VelioraSuite v1.6.0 - Local Check Report

Checked before packaging:

- Root ZIP structure checked: only `.github`, `src`, `.gitignore`, `README.md`, `pom.xml`, `TEST_REPORT.md`.
- No `.java` file placed in repository root.
- YAML files parsed successfully with Python YAML parser.
- Braces in Java files were checked with a static balance check.
- `plugin.yml` version updated to `1.6.0`.
- `pom.xml` version updated to `1.6.0`.
- Module resources are present in `src/main/resources/modules/`.
- Config files include comments explaining how to edit/use the feature.

Main fixes in this build:

- VelioraTeam: `/vteam disband`, missing owner leave message, team prefix placeholders.
- VelioraFishing: actionbar timing minigame and `/vf sell` GUI.
- VelioraClearLag: item stack amount counting and mob cluster cleaner.
- VelioraQuest: GUI, clearer targets, more quest categories.
- VelioraSkills: quest-based skill EXP, actionbar, PlaceholderAPI placeholders.
- VelioraTrader: NPC-style villager trader, saved locations, auto despawn.
- VelioraBoss: rarity, bossbar, skills, true damage, last hit/top damage rewards.
- VelioraGuide: optional blank-line clear when switching pages.

Limit:

- Maven/Paper compile cannot be executed inside this sandbox because Maven and external Paper/Vault dependencies are unavailable offline.
- The included GitHub Actions workflow is the intended real build check.
