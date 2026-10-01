# webstorm-deno-tasks

Green gutter **Run / Debug** icons for `tasks` in `deno.json` / `deno.jsonc` in WebStorm, like npm scripts in `package.json`.

> [!WARNING]
> **Fully vibe-coded.** An AI wrote this plugin with almost no human review. **Use it at your own risk.**
>
> **Don't use it once [WEB-61627](https://youtrack.jetbrains.com/issue/WEB-61627) ("Support running tasks from deno.json") is resolved.** It's only a stopgap until WebStorm supports this natively.

```jsonc
{
  "tasks": {
    ▶ "dev": "deno run --watch --allow-net main.ts"
  }
}
```

## Install
Requires WebStorm **2026.2** (build 262.*) with the bundled Deno plugin enabled.

1. Download `webstorm-deno-tasks-<version>.zip` from [Releases](../../releases).
2. Go to **Settings → Plugins → ⚙ → Install Plugin from Disk…**, pick the zip, and restart.

## What it does
- **Run:** `deno task --config <deno.json> <task> [args]`
- **Debug:** reruns the task's own command via `deno task --eval`, adding `--inspect-brk=127.0.0.1:<port>` after the first `deno run|serve|test`, then attaches WebStorm's JavaScript debugger.
  - Watch flags (`--watch`, `--hmr`, …) are removed while debugging, because a reload ends the inspector session and the debugger can't reattach. Rerun to pick up changes.
  - Tasks that don't start Deno via `deno run|serve|test` can't be debugged (for example `vite`, or a task that only calls `deno task other`). Object-form task `dependencies` are skipped in Debug.
- Each task gets a **Deno Task** run configuration you can edit: arguments and environment variables.
- The `deno` executable comes from the Deno plugin settings, then PATH, then `~/.deno/bin`.

## Build
Plain `javac` against a local WebStorm install, no Gradle. Requires JDK 21+.

```bash
WEBSTORM="/path/to/webstorm" bash build.sh   # -> dist/webstorm-deno-tasks-<version>.zip
```

Set `USER_PLUGINS` too if your WebStorm config dir isn't `%APPDATA%\JetBrains\WebStorm2026.2\plugins`. `build.sh` assumes Windows (`;` classpath separator).

## How it works (for contributors / agents)
`src/local/denotasks/`:
- `DenoTaskRunLineMarkerContributor`: puts the icon on each key of the top-level `tasks` object, using `ExecutorAction.getActions()`.
- `DenoTaskConfigurationProducer`: builds a run config from the PSI context (config file path + task name).
- `DenoTaskConfigurationType`: the "Deno Task" config type. Icon is `DenoUtil.getDefaultDenoIcon()`; `DenoIcons.Deno` is 40px, too big for the run widget.
- `DenoTaskRunConfiguration`: stores configPath, taskName, arguments and env. Builds the command lines and implements `NodeJSDebuggableConfiguration`.
- `DenoTaskRunState`: `NodeDebuggableRunProfileState`. Both Run (`NodeRunProgramRunner`, configurator = null) and Debug (`NodeDebugProgramRunner`, port configurator) call `execute(CommandLineDebugConfigurator)`, so **the executor id decides which command line is built**.
- `DenoTaskUtil`: PSI helpers, `readTaskCommand`, `injectInspectFlag`.

Gotchas:
- `hasConfiguredDebugAddress()` **must return `true`**, as in Deno's own `DenoRunConfiguration`. Otherwise the Node debug runner attaches via NODE_OPTIONS + `debugConnector.js` (ignored by Deno), and no debug port is ever passed.
- Deno has no env var that enables the inspector, so the flag has to go into the task's own command.
- `plugin.xml` uses v2 `<dependencies>`. `intellij.javascript.backend` and `intellij.javascript.debugger.backend` are separate content modules and must be listed explicitly.
- Only replace the installed jar while WebStorm is closed. To check it loaded, look in `idea.log` for "Loaded custom plugins: … Deno Tasks Runner (x.y.z)".
- `until-build="262.*"`: bump it and rebuild for new IDE versions. There are no platform sources, so inspect the IDE jars with `javap`.

## License
[MIT](LICENSE)
