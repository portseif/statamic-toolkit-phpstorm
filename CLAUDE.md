# CLAUDE.md

Guidance for Claude Code (`claude.ai/code`) when working in this repository.

## Project Overview

**Statamic Toolkit** is a JetBrains IDE plugin for the [Antlers](https://statamic.dev/frontend/antlers) template language in Statamic CMS.

- Marketplace/display name: **Statamic Toolkit**
- Language name (file type, parser, highlighter, color scheme, code style): **Antlers**
- Settings tree label (under Languages & Frameworks): **Statamic**
- Development target: **PhpStorm** (`platformType=PS` in `gradle.properties`)

## Build and Environment

```bash
./gradlew build          # Generate lexer/parser from grammars + compile + package
./gradlew runIde         # Launch sandbox IDE with plugin installed
./gradlew buildPlugin    # Package plugin for distribution
./gradlew test           # Run tests (JUnit 4)
./gradlew test --tests com.antlers.support.formatting.AntlersFormattingPostProcessorTest  # Focused formatter regressions
```

Grammar-Kit regenerates code from `.flex` and `.bnf` automatically before compile — no separate generation step.

### JDK and Platform

- **JDK 21 required**. The Gradle toolchain (`jvmToolchain(21)`) enforces this via `javaVersion = 21` in `gradle.properties`.
- IntelliJ IDEA CE's bundled JBR (Java 25) is too new — install JDK 21 separately.
- For terminal builds: `export JAVA_HOME="/Users/$USER/Library/Java/JavaVirtualMachines/jbr-21.x.x/Contents/Home"`.
- **Platform target**: PhpStorm 2025.1 (`platformVersion = 2025.1`). `sinceBuildVersion = 242` keeps 2024.2 compatibility. Grammar-Kit does not yet work with PhpStorm 2026.1.

### Plugin Dependencies

Runtime dependencies must be declared in BOTH `build.gradle.kts` (`bundledPlugin()`) AND `plugin.xml` (`<depends>`). Missing either side silently fails features. Optional dependencies use `<depends optional="true" config-file="...">`.

`<depends>` only accepts **plugin IDs** (e.g. `com.jetbrains.php`, `JavaScript`), never IntelliJ v2 module names (e.g. `intellij.platform.lsp`). Using a module name silently fails — register core platform APIs directly in `plugin.xml` instead.

### PHP Discovery

The plugin finds PHP at Herd (`~/Library/Application Support/Herd/bin/php`), Homebrew (`/opt/homebrew/bin/php`), or system PATH. Herd paths contain spaces; `GeneralCommandLine` handles quoting automatically.

### Packaging

`./gradlew buildPlugin` produces `build/distributions/statamic-toolkit-x.y.z.zip`. A `bumpPatchVersion` task runs first and auto-increments the patch version in `gradle.properties`. Major/minor bumps stay manual.

## File Layout

- Hand-written Kotlin: `src/main/kotlin/`
- Generated code: `src/main/gen/` (gitignored, regenerated on build)
- Grammar sources: `grammars/Antlers.flex`, `grammars/Antlers.bnf`
- Plugin manifest: `src/main/resources/META-INF/plugin.xml`
- Optional dependency config: `src/main/resources/META-INF/antlers-php.xml`
- Color schemes: `src/main/resources/colorSchemes/`
- Bundled LSP server: `src/main/resources/language-server/antlersls.js`
- Intention descriptions: `src/main/resources/intentionDescriptions/`

Hand-maintained data files:

- Tag parameters: `src/main/kotlin/com/antlers/support/statamic/StatamicTagParameters.kt`
- Scope variables: `src/main/kotlin/com/antlers/support/statamic/StatamicScopeVariables.kt`
- Block tag registry: `src/main/kotlin/com/antlers/support/AntlersBlockTags.kt`
- Storage conversion service: `src/main/kotlin/com/antlers/support/statamic/StatamicStorageConversionService.kt`
- Storage conversion dialogs: `src/main/kotlin/com/antlers/support/settings/StatamicStorageConversionDialogs.kt`

## Core Architecture

### Dual PSI Tree

Antlers files mix Antlers expressions (`{{ }}`) and HTML/CSS/JS, so the plugin uses IntelliJ's Template Language Framework with `MultiplePsiFilesPerDocumentFileViewProvider`. Each file holds two PSI trees:

- **Antlers tree** (base language): tags, expressions, conditionals, modifiers
- **HTML tree** (template data language): full HTML/CSS/JS intelligence

`AntlersFileViewProvider` overrides `findElementAt()` and `findReferenceAt()` to prefer the HTML tree for template data regions and fall back to the Antlers tree.

The `TemplateDataElementType` outer fragment **must** use `OuterLanguageElementType`. A plain PSI element type causes `Wrong element created by ASTFactory` on reparse/file switch.

### Grammar and Code Generation

Two grammars in `grammars/` generate into `src/main/gen/`:

1. `Antlers.flex` (JFlex) → `_AntlersLexer.java`
2. `Antlers.bnf` (Grammar-Kit) → `AntlersParser.java` plus PSI element types/interfaces/impls

Lexer states: `YYINITIAL`, `ANTLERS_EXPR`, `COMMENT`, `PHP_RAW`, `PHP_ECHO`, `DQ_STRING`, `SQ_STRING`, `NOPARSE`.

**Token identity bridge**: the parser and lexer must share the same `IElementType` instances. `AntlersTokenTypes.factory()` uses reflection to resolve BNF token names to existing lexer fields. The BNF `tokens` block must list bare token names (e.g. `ANTLERS_OPEN`), not display strings (`{{`).

#### Adding a new keyword

1. Add a lexer rule in `Antlers.flex` with lookahead: `"keyword" / [^a-zA-Z0-9_]`
2. Add a token field in `AntlersTokenTypes.kt`: `@JvmField val KEYWORD_X = AntlersTokenType("KEYWORD_X")`
3. Add the token to `AntlersTokenSets.KEYWORDS`
4. Add the bare token name to the `tokens` block in `Antlers.bnf` and reference it in grammar rules
5. Run `./gradlew build`

#### BNF rules of thumb

- `pin=1` commits after the first matched token and prevents backtracking.
- `recoverWhile` skips tokens until a predicate matches. Don't apply it to rules that can fail cleanly without consuming tokens.
- `private` rules don't produce PSI nodes — useful for dispatch/grouping.
- If a rule accepts only `IDENTIFIER` but the lexer emits a keyword token (e.g. `KEYWORD_IF` for `if`), add a private `tagNameAtom` rule listing `IDENTIFIER | KEYWORD_IF | KEYWORD_UNLESS | ...`. Keep keywords context-free in the lexer.

**Closing-tag gotcha**: `{{ /if }}` requires the `tagNameAtom` trick above. Once `{{ /if }}` parses as `closingTag`, `AntlersFoldingBuilder` must remap `if → COND_IF` and `unless → COND_UNLESS` so keys match the opening conditionals.

## Language Server (LSP)

The plugin bundles the [Stillat Antlers Language Server](https://github.com/Stillat/vscode-antlers-language-server) at `src/main/resources/language-server/antlersls.js`. `AntlersLspServerSupportProvider` triggers on `.antlers.html`/`.antlers.php` files; `AntlersLspServerDescriptor` extracts the JS to a temp directory and launches it with `node --stdio`. Requires Node.js (found via Herd, Homebrew, or PATH).

**Native PSI is the source of truth for completion, hover, and go-to-definition. LSP provides diagnostics only.**

### Rules

- Registered directly in `plugin.xml`, not via optional dependency. **Do not put `intellij.platform.lsp` in `<depends>`** — it's a module name, not a plugin ID, and silently fails.
- `lspCompletionSupport` disabled — it would duplicate `AntlersCompletionContributor`.
- `lspFormattingSupport` returns `null` — the Stillat server flattens indentation inside `<script>`/`<style>` blocks. Don't re-enable without fixing the upstream behavior.
- `lspDocumentLinkSupport` returns `null` — the server publishes `textDocument/documentLink` for every `partial:name` tag, which IntelliJ renders as persistent underlined hyperlinks over the whole tag. Partial navigation is served natively by `AntlersGotoDeclarationHandler`; don't re-enable without proving the underlines are gone.
- `lspHoverSupport` and `lspGoToDefinitionSupport` disabled — native PSI is richer and avoids the round-trip.
- The extracted server is patched to remove the VS Code-specific `antlers/projectDetailsAvailable` request.

### Rebuilding from upstream

```bash
cd /tmp && git clone --depth 1 https://github.com/Stillat/vscode-antlers-language-server.git antlers-lsp
cd antlers-lsp && npm install && npm run bundle:antlersls
cp antlersls/server.js /path/to/antlers-support/src/main/resources/language-server/antlersls.js
```

## Editor Features

### Syntax Highlighting

`AntlersEditorHighlighter` extends `LayeredLexerEditorHighlighter` with:

- Base layer: `AntlersSyntaxHighlighter`
- `TEMPLATE_TEXT` layer: HTML/CSS/JS syntax highlighter

### Semantic Highlighting

`AntlersHighlightingAnnotator` colors identifier parts within `AntlersTagName` nodes. `isTagLike()` distinguishes real tags (namespaced, parameterized, closing, or known block tags) from simple variables like `{{ title }}`.

Tag-name separators use distinct text attributes:

- `:` in tag names (`collection:count`, `form:create`, `partial:hero`) → `DELIMITER` (same as `{{`/`}}`), to set off the namespace halves
- `/` in partial paths (`partial:components/hero`) → `TAG_PATH`, a separate key so users can style path segments distinctly

The bundled `ANTLERS_PARTIAL_PATH` and `ANTLERS_TAG_HEAD` keys (in both Darcula and Default schemes) default to the same color with no underline or other effect. When editing separator coloring, update the `AntlersEditorHighlighterTest` assertions that check which tokens land in each attribute bucket.

**Resetting user overrides when changing a bundled color's appearance**: editing the XML alone is not enough — users who customized the scheme have a saved override keyed by the attribute external ID. The fix is to **rename the attribute external ID** (e.g. `ANTLERS_TAG_PATH` → `ANTLERS_PARTIAL_PATH`) in both `AntlersHighlighterColors.kt` and the bundled scheme XMLs. The old key is orphaned in the user's saved scheme; the new key picks up the new default. Update any tests or other consumers that reference the external ID string.

### Formatting

Two layers:

1. **`AntlersFormattingModelBuilder` / `AntlersBlock`** — token spacing via `TemplateLanguageFormattingModelBuilder`. `AntlersFormattingModelBuilder` special-cases `OuterLanguageElementType` nodes and delegates them to `SimpleTemplateLanguageFormattingModelBuilder`.
2. **`AntlersConditionalPostFormatProcessor`** — standalone Antlers line indentation plus a corrective pass for `<script>` content.

`AntlersBlock` enforces: one space inside `{{ }}`, one space around operators, no space around `=` (parameters), `:` (modifier args), or `/` (paths).

**`OP_DIVIDE` spacing**: use `.around(OP_DIVIDE).none()`. `/` is usually a path separator in Antlers; `.none()` actively removes spaces. Omitting the rule returns `null` and leaves whitespace untouched.

**`AntlersBlock` indent rules**:

- `getIndent()` returns `NormalIndent` for `TAG_EXPRESSION`/`CLOSING_TAG`, `NoneIndent` otherwise.
- `getChildAttributes()` must delegate to `super.getChildAttributes(newChildIndex)`. Hardcoding `NoneIndent` breaks how `DataLanguageBlockWrapper` children inherit indent context from the underlying HTML/JS/CSS formatter.

**Structural indentation (post-format processor)**: a depth stack over both standalone Antlers and HTML lines. Only real nesting constructs (HTML parents, `if`/`unless`/`switch`/`else`/`elseif`) create indentation. Flat sequences of standalone tags (e.g. consecutive `{{ partial:... }}`) stay aligned.

**`<script>` and `<style>` indentation**: the Template Language Framework doesn't propagate indent context from HTML → JS/CSS for template files, so the native formatter leaves top-level JS/CSS at column 0 inside `<script>`/`<style>`. The post-format processor applies a corrective pass tracking `insideInlineBlock` state. For `<script>`:

- `updateScriptIndentState()` per line maintains a minimal JS state machine: brace depth, string state (single/double/template), comment state (line/block). Braces inside strings/comments don't affect indent.
- `desiredScriptIndentLevel()` returns `baseIndentLevel + braceDepth - leadingClosers`, where `baseIndentLevel` is the HTML nesting depth of the `<script>` tag and `leadingClosers` handles lines starting with `}`.
- The processor **only raises** indent (via `indentWidth` comparison) — it never strips existing indentation from the user or the built-in formatter.

For `<style>`, the built-in CSS formatter handles everything; the processor just tracks block boundaries. Full JS/CSS formatter delegation would require reworking the template language block chain.

Rules:

- Do not remove the `hasInlineScriptBlocks` early-out in `processRange()` — without it, pure HTML+JS files (no Antlers tags) skip the processor and the JS indent fix never runs.
- `ScriptIndentState` is stateful across lines within one script block — always reset to `null` on `</script>` close.
- Adding new inline-content tags (e.g. `<template>`) requires updating `INLINE_CONTENT_TAGS` and deciding whether the brace-counting indenter applies.

### Code Folding

`AntlersFoldingBuilder` matches opening/closing tags in document order using a stack (the PSI tree is flat). Regular tags use `tagName.text` as stack keys; conditionals use synthetic keys `COND_IF`/`COND_UNLESS` to avoid collisions. `getPlaceholderText()` shows the expression text, truncated at 60 characters.

### Typing Aids

**Auto-close `{{ }}`**: `AntlersTypedHandler` produces `{{  }}` with the cursor between the spaces when the user types `{{`. Toggled by `AntlersSettings.enableAutoCloseDelimiters`. Features that emit Antlers output (live templates, snippets, intentions) must account for the `}}` already present after the cursor. The handler also removes stray `}` from IntelliJ's single-brace pairing and avoids double-closing existing `}}`.

**Auto-close tag on `/`**: typing `{{ /` scans backward over raw document text to find the nearest unclosed block tag and auto-completes the name. Only tags in `AntlersBlockTags.NAMES` are considered.

**Auto-indent on Enter**: `AntlersEnterHandler.postProcessEnter()` adds one indent level after a block tag opener (via `AntlersBlockTags.isBlockTag()`).

### Alpine.js Integration

Implemented with `AntlersAlpineAttributeInjector` (MultiHostInjector), `AntlersAlpineReferenceContributor`, and `AntlersAlpineReferenceResolver`.

- `x-data` is injected as a JS expression wrapped in `( )` so object literals parse.
- Event-style attributes (`@click`, `x-on:*`, `x-init`, `x-effect`) inject as statements.
- `x-for` is **not** injected — Alpine's `(item, index) in items` is invalid JS. Loop aliases resolve manually in the resolver.
- Cmd-click on Alpine method calls resolves through PSI references first; `AntlersGotoDeclarationHandler` is a fallback for Antlers-specific navigation.
- `AntlersAlpineReferenceContributor` must stay cheap — don't eagerly call the full resolver from `getReferencesByElement()`.
- `AntlersAlpineReferenceResolver` caches the injected `x-data` object lookup per `XmlAttributeValue`. Don't re-materialize injected PSI while walking ancestors.
- `AntlersAlpineXmlSuppressionProvider` suppresses false XML/HTML warnings on Alpine attributes (`:`, `@`, `x-` prefixes). Update `isAlpineAttribute()` when adding new prefixes.

### PHP Injection

`AntlersPhpInjector` injects PHP into `{{? ?}}` (raw) and `{{$ $}}` (echo) blocks. Registered as an optional dependency on `com.jetbrains.php` in `antlers-php.xml`.

- `{{? ... ?}}` → prefix `<?php `, suffix ` ?>`
- `{{$ ... $}}` → prefix `<?php echo `, suffix `; ?>`
- The BNF rules use `AntlersPhpBlockMixin`, which implements `PsiLanguageInjectionHost`.

### Block Tag Registry

`AntlersBlockTags` is a shared `Set<String>` of tag names that accept a closing `{{ /tag }}`. Consumed by the post-format processor, enter handler, typed handler, structure view, and completion contributor. Add new block tags to `AntlersBlockTags.NAMES` — all consumers pick them up automatically.

## Data, Completion, and Documentation

### Statamic Catalog

Official Statamic tags, modifiers, and variables are generated, not hand-maintained.

- Source of truth: `scripts/generate_statamic_catalog.py`
- Generated output: `src/main/kotlin/com/antlers/support/statamic/StatamicCatalogGenerated.kt`
- Runtime lookup: `StatamicCatalog`

Powers `StatamicData`, `AntlersCompletionContributor`, `AntlersDocumentationProvider`, and official description/example/URL metadata.

**Entry fields**: Statamic's public docs don't enumerate per-entry fields (`content`, `title`, `id`, `slug`, `permalink`, …), so the scraper misses them. Since they're always valid at a template's top level (Antlers templates render in an entry context), they're surfaced via a second source:

- `StatamicScopeVariables.ENTRY_FIELDS` — the canonical list (`internal val`, used by both scope-aware and top-level lookups)
- `StatamicCatalog.findVariable(name)` — falls back to an entry-fields map built from `ENTRY_FIELDS` when no generated variable matches. Powers hover docs.
- `StatamicData.VARIABLES` — merges `GENERATED_VARIABLES` with `ENTRY_FIELDS` via `distinctBy { it.name }` (generated wins on collision). Powers top-level completion via `VARIABLE_ELEMENTS`.

To add a new always-available entry field, append to `ENTRY_FIELDS` — the catalog, completion list, and scope-aware completion all pick it up automatically.

### Completion

**Pre-building**: `StatamicData` keeps lazy, pre-built `LookupElement` lists (`TAG_ELEMENTS`, `MODIFIER_ELEMENTS`, `VARIABLE_ELEMENTS`, `SUB_TAG_ELEMENT_MAP`). Always call `result.addAllElements(StatamicData.XXX_ELEMENTS)` — don't construct `LookupElement` instances per keystroke.

**Auto-popup**: `AntlersCompletionContributor.invokeAutoPopup()` returns `true` for `:` so typing `{{ partial:` or `{{ collection:` triggers the popup immediately.

**Tag parameter completion**: `StatamicTagParameters` (hand-maintained) holds parameter metadata. `StatamicData.getParameterElements(tagName)` builds `LookupElement`s with an `InsertHandler` that appends `=""` and positions the caret. Required parameters render bold. The contributor finds parameter context via `PsiTreeUtil.getParentOfType()` and filters out already-used names.

**Scope-aware variable completion**: `StatamicScopeVariables` defines variables available inside tag pair loops (loop variables like `first`/`last`/`count`, plus tag-specific fields). The contributor walks the flat PSI with a stack to find the nearest enclosing block tag.

### Collection Handle Discovery

`StatamicProjectCollections` is a project-level service that discovers collection handles and other Statamic resources from both flat-file and Eloquent sources. It also indexes blueprint entry fields for collection-scoped completion.

- **Flat-file**: scans `content/collections/`, `content/navigation/`, `content/taxonomies/`, etc.
- **Eloquent**: runs `php artisan tinker --execute` to query Statamic Facades
- **Entry fields**: blueprint YAML scanned locally for both drivers
- **Driver detection**: reads `config/statamic/eloquent-driver.php` and checks whether `collections.driver` is `'eloquent'`
- **Background execution**: `Task.Backgroundable` (see Performance and Stability)
- **Auto-index**: optional VFS file watcher with 2-second debounce

## Storage Conversion

`StatamicStorageConversionService` converts Statamic sites between flat-file and Eloquent (database) storage via `StorageConversionEngine`.

### Architecture

- **`StatamicStorageConversionService`** — project-level facade with a 10-second `StorageSnapshot` cache. Exposes `analyze()`, `convert()`, `currentStorageOverview()`.
- **`StorageConversionEngine`** — stateless worker for snapshot capture, analysis, validation, backup, and conversion. Takes a `CommandExecutor` interface for testability.
- **Dialogs** (`StatamicStorageConversionDialogs.kt`) — `StorageConversionConfirmationDialog` (pre-conversion analysis) and `StorageConversionProgressDialog` (live progress).

Data types:

- `StorageSnapshot` — resource counts/metrics for one driver at a point in time
- `StorageConversionAnalysis` — source/target snapshots, conflicts, supported resolutions (MERGE/OVERWRITE/CANCEL)
- `StorageConversionRequest` — target driver, conflict resolution, optional `DatabaseConnectionConfig`

Phases: `ANALYZING → VALIDATING → BACKUP → MIGRATING → VERIFYING → COMPLETE`.

Backups go to `storage/statamic-toolkit/backups/`. Logs go to `storage/logs/statamic-toolkit-conversion-{timestamp}.log`. Concurrent conversions are blocked via `storage/statamic-toolkit/conversion.lock` (`FileChannel.lock()`).

The Data Source settings page shows contextual buttons — "Convert to database" (with `DatabaseConfigDialog` for `.env` setup) on flat-file, "Convert to flat file" on Eloquent.

### Artisan Command Flags

Many `eloquent:export-*` and `eloquent:import-*` commands have `confirm()` prompts that silently return `false` under `--no-interaction`, causing the command to skip all work while printing a success message. Commands that support `--force` must always receive it. The `COMMANDS_WITH_FORCE` set tracks which commands accept it; commands not in this set (e.g. `export-entries`, `export-sites`) will reject the flag.

### Critical vs Non-critical Commands

`CRITICAL_EXPORT_COMMANDS` and `CRITICAL_IMPORT_COMMANDS` (`export-collections`, `export-entries` and their import counterparts) are fatal on failure. All other commands are wrapped in try-catch — failures from missing tables or binding exceptions in partially-set-up projects are logged and execution continues.

### Orphaned Entry Cleanup

Before `eloquent:export-entries`, the engine runs an orphan cleanup via `artisan tinker` using `Collection::findByHandle()` (Statamic's own facade) to match exactly what `export-entries` does internally. Entries referencing unresolvable collections are deleted. The `analyze()` step also counts orphans and surfaces a warning.

### Driver Config Rewrite

`eloquent-driver.php` uses `env()` and `\Statamic\...\Model::class`, so bare `php -r` cannot `require` it. Rewriting runs via `artisan tinker` with a `preg_replace` on `'driver' => '...'` patterns, preserving the original file structure.

### Post-conversion UI Refresh

Three things happen after a successful conversion:

1. **Immediate UI update**: the `onFinished` callback sets the driver label and button visibility directly from the conversion target.
2. **VFS refresh**: `refreshCollections()` calls `projectDir?.refresh(true, true)` so the flat-file scanner sees externally-created files.
3. **Background re-index**: `StatamicProjectCollections.refresh()` re-detects the driver and re-indexes; `notifyListeners()` uses `ModalityState.any()` so listeners dispatch during modal dialogs.

### Verification

Post-conversion verification compares source and target snapshots. Mismatches are returned as informational messages, not thrown as exceptions. This prevents partial exports (from non-critical command failures) from triggering a full rollback.

### Validation Resilience

After converting to flat-file, Statamic may fail to boot via `artisan` (e.g. null site in `Cascade` if sites data wasn't exported). The `analyze()` step must handle this gracefully:

- If `availablePleaseCommands()` returns empty, skip command availability checks with a warning instead of blocking.
- `runMigrationDryRun()` failures are warnings, not errors — the actual `artisan migrate` runs during conversion.
- `captureDatabaseSnapshot()` failures when inspecting the target return an empty snapshot — the tables get created during conversion via `artisan migrate`.

### Dialog Rules

- **Do not start execution from a `DialogWrapper` constructor or `init` block.** The modal event loop hasn't started, so UI updates queue up and never dispatch. Use a `windowOpened` listener.
- Both convert buttons are disabled while commands run, to prevent duplicate threads.
- The `CommandExecutor` interface lets tests substitute a mock executor. See `StatamicStorageConversionServiceTest.kt`.

## UI and IDE Integration

### Settings

Feature toggles live in `AntlersSettings` (`PersistentStateComponent`), exposed at **Settings > Languages & Frameworks > Statamic**.

**Adding a new toggle**:

1. Add `var enableXxx: Boolean = true` to `AntlersSettings.State`
2. Add a `JBCheckBox` in the appropriate sub-page
3. Wire through `isModified`, `apply`, `reset`
4. Guard the feature entry point with `if (!AntlersSettings.getInstance().state.enableXxx) return`

Each sub-page binds checkboxes via `CheckboxField(box, read, write)`. The `fields` list is built once; `isModified`, `apply`, and `reset` collapse to `any`/`forEach` over it.

#### Nested sub-pages

Sub-pages (Data Source, Editor, Completion, Navigation & Documentation, Language Injection) are separate `Configurable` classes, each registered in `plugin.xml` with `parentId="com.statamic.toolkit.settings"`.

- `Configurable.Composite` alone does **not** create tree children — each child must be registered as an `applicationConfigurable` with the correct `parentId`.
- To navigate within an open settings dialog, walk the Swing hierarchy to find `SettingsEditor` and call `editor.select(configurable)`. Do **not** use `ShowSettingsUtil.showSettingsDialog()` from within a settings page — it opens a second dialog.

#### FormBuilder label alignment

`FormBuilder.addLabeledComponent(label, component)` vertically centers the label against the right column. When the right column is a tall expandable panel (e.g. the Data Source "Locations" accordion), the label floats in the middle. Fix it on the label:

```kotlin
JBLabel("Locations:").apply { verticalAlignment = SwingConstants.TOP }
```

`JLabel.verticalAlignment` is respected by the GridBagConstraints FormBuilder uses internally, so the label pins to the top of its row without needing a different layout manager.

#### Clickable label pattern

Both the status bar widget and the Data Source page use the same "click a name to copy it" idiom: `JBLabel` + hand cursor + mouse listener that swaps `foreground` between `JBUI.CurrentTheme.Label.disabledForeground()` and `JBUI.CurrentTheme.Link.Foreground.ENABLED` on enter/exit, and flashes link color for 600ms on click (`javax.swing.Timer(600) { foreground = original }.apply { isRepeats = false; start() }`). Match the pattern for visual consistency.

### Statamic Menu

Top-level **Statamic** menu for PHP-side workflows. Content query snippets are enabled only in PHP files. Generators create files under `app/Http/Controllers`, `app/Tags`, `app/Modifiers` and open existing files instead of overwriting. Menu visibility is handled by `StatamicProjectActionGroup` and `StatamicPhpInsertActionGroup`.

### Navigation

`AntlersGotoDeclarationHandler` resolves:

- Unknown tags → `app/Tags/ClassName.php`; unknown modifiers → `app/Modifiers/ClassName.php`. Built-in tags and block tags are skipped. File lookup uses `FilenameIndex` with a directory-scoped `GlobalSearchScope`.
- `{{ partial:path/to/file }}` → the matching `resources/views/**/path/to/file.antlers.{html,php}` via `AntlersPartialPaths.matchingPsiFiles`.

Partial navigation is intentionally **not** implemented as a `PsiReference`. `PsiReference`s cause IntelliJ to show a hover-underline on the referenced text, and the LSP server's `documentLink` channel (now disabled) did the same thing statically. Running navigation through `GotoDeclarationHandler` keeps Cmd-click working without any visual underline on partial tags.

**Known limitation**: tags with `protected static $handle = 'custom_name'` decouple the class name from the tag name; filename-based lookup misses these.

### Extract Partial Intention

`ExtractPartialIntention` extracts selected text into `resources/views/partials/{name}.antlers.html` and replaces it with `{{ partial:{name} }}`. Available when the file is `.antlers.html`/`.antlers.php` and the editor has a selection.

### Status Bar Widget

`StatamicStatusBarWidgetFactory` shows a popup (`JBPopupFactory.createComponentPopupBuilder()`) with driver type, indexing status, resource counts, an auto-index checkbox, and a refresh button.

Resource groups (Collections, Navigations, etc.) are **collapsible** via `+`/`−` disclosure toggles. Clicking a header toggles `handlesRow.isVisible`, swaps the glyph, and calls `SwingUtilities.getWindowAncestor(row)?.pack()` to resize the popup.

The handle list uses a custom `WrapLayout` (defined at the top of the file) that extends `FlowLayout` and overrides `preferredLayoutSize()`/`minimumLayoutSize()` to report wrapped dimensions. Standard `FlowLayout` reports a single-line preferred size inside a `BoxLayout` parent and never wraps. `handlesRow.getPreferredSize()` is also overridden to cap width at `parent?.width ?: JBUI.scale(360)` so the popup doesn't stretch to fit a long single line.

### Structure View and Navigation Bar

- **Structure View**: `AntlersStructureViewElement` builds a virtual nested tree from the flat PSI by matching opening/closing pairs with a stack. HTML landmark elements (`<header>`, `<main>`, `<nav>`, etc.) are merged by document offset.
- **Navigation bar**: `AntlersStructureAwareNavbar` extends `StructureAwareNavBarModelExtension`. Use `override val language: Language`, not `override fun getLanguage()`. `getIcon()` does not exist on the superclass.

### Find Usages

`AntlersFindUsagesProvider` uses `DefaultWordsScanner` from `com.intellij.lang.cacheBuilder`.

## Performance and Stability

- **Search scopes**: partial navigation and completion must use `AntlersPartialPaths.searchScope(project)` rooted at `resources/views`, not `GlobalSearchScope.allScope(project)`. Do not add fallback `FilenameIndex` scans over the whole project.
- **Lexer EOF handling**: JFlex states with custom start conditions need explicit `<<EOF>>` handling. Truncated Antlers blocks should reset to `YYINITIAL` and return `BAD_CHARACTER`.
- **Incremental reparse**: `AntlersFileViewProvider.supportsIncrementalReparse()` is intentionally `false`. Do not change without a reproducible case and validation.
- **No processes in ReadAction**: completion handlers run under ReadAction. Use `Task.Backgroundable` to pre-cache results; the completion handler only reads the cache.
- **Post-format processor PSI tree**: call `file.viewProvider.getPsi(AntlersLanguage.INSTANCE)` — the `file` parameter may be the HTML PSI, yielding zero Antlers nodes.
- **`invokeLater` and modal dialogs**: `application.invokeLater(runnable)` without `ModalityState` silently drops runnables while a modal dialog is open. Use `SwingUtilities.invokeLater` or pass `ModalityState.any()`.
- **VFS refresh after external processes**: `LocalFileSystem` VFS caches file state. Files created by external commands (artisan, please) are invisible until refreshed. Call `findFileByPath(path)?.refresh(true, true)` before any VFS-based scan that needs to see externally-created files.
- If the IDE freezes, collect a thread dump or CPU snapshot before making speculative changes.

## Release and Maintenance

Plugin update notes come from `CHANGELOG.md`, not `<change-notes>` in `plugin.xml`. `build.gradle.kts` extracts version sections and feeds them into `pluginConfiguration.changeNotes`. Verify the generated block in `build/tmp/patchPluginXml/plugin.xml`.

Every version bump must update:

1. `pluginVersion` in `gradle.properties` — patch is auto-bumped by `buildPlugin`; set major/minor manually
2. A new `## [x.y.z]` section at the top of `CHANGELOG.md`
3. `README.md` features list and roadmap, if the release adds visible features
4. `CLAUDE.md`, if the release adds new subsystems or patterns
