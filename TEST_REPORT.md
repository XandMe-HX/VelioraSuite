# VelioraSuite v1.5.0 - Local Check Report

Checked before packaging:

- Root ZIP structure: only `.github`, `src`, `.gitignore`, `README.md`, `pom.xml`, `TEST_REPORT.md`.
- No `.java` file placed in repository root.
- YAML files parsed successfully with Python YAML parser.
- `plugin.yml` contains commands for every registered module command.
- Module resources are present in `src/main/resources/modules/`.
- Config files include comments explaining how to edit/use the feature.

Limit:

- Maven/Paper compile cannot be executed inside this sandbox because Maven and external Paper/Vault dependencies are unavailable offline.
- The included GitHub Actions workflow is the intended real build check.
