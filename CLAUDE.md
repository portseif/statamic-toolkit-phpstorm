# CLAUDE.md

This file provides guidance to Claude Code (`claude.ai/code`) when working in this repository.

## Project Overview

**Statamic Toolkit** is a JetBrains IDE plugin for the [Antlers](https://statamic.dev/frontend/antlers) template language in Statamic CMS.

- Marketplace/plugin display name: **Statamic Toolkit**
- Language name: **Antlers**
- Current development target: **PhpStorm** (`platformType=PS` in `gradle.properties`)
- Language-level registrations (file type, parser, highlighter, color scheme, code style) use **Antlers**
- The settings panel under Languages & Frameworks uses **Statamic**

## Build and Environment

```bash
./gradlew build          # Generate lexer/parser from grammars + compile + package
./gradlew runIde         # Launch sandbox IDE with plugin installed
./gradlew buildPlugin    # Package plugin for distribution
./gradlew test           # Run tests (JUnit 4)
./gradlew test --tests com.antlers.support.formatting.AntlersFormattingPostProcessorTest  # Focused formatter regressions
```

Grammar-Kit code generation from `.flex` and `.bnf` files runs automatically before compilation. There is no separate generation step.

Runtime plugin dependencies must be declared in both places:

- `build.gradle.kts` via `bundledPlugin()`
- `plugin.xml` via `<depends>`

If either side is missing, features can silently fail. Optional dependencies must use `<depends optional="true" config-file="...">`.

**Important**: `<depends>` only accepts **plugin IDs** (e.g. `com.jetbrains.php`, `JavaScript`), not IntelliJ v2 module names (e.g. `intellij.platform.lsp`). Using a module name silently fails — the optional config file never loads. For platform APIs that are part of the core (like the LSP API in 2025.1+), register extensions directly in `plugin.xml`.

### JDK and Platform

- **JDK 21 required** — set in `gradle.properties` as `javaVersion = 21`. The Gradle toolchain (`jvmToolchain(21)`) enforces this.
- IntelliJ IDEA CE's bundled JBR (Java 25) is too new for the Gradle build. Install JDK 21 separately.
- Set `JAVA_HOME` when building from terminal: `export JAVA_HOME="/Users/$USER/Library/Java/JavaVirtualMachines/jbr-21.x.x/Contents/Home"`
- **Development target is PhpStorm 2025.1** (`platformVersion = 2025.1`). The `sinceBuildVersion = 242` keeps compatibility back to 2024.2. Grammar-Kit does not yet work with PhpStorm 2026.1.

### PHP Discovery

The plugin finds PHP at Herd (`~/Library/Application Support/Herd/bin/php`), Homebrew (`/opt/homebrew/bin/php`), or system PATH. Herd paths contain spaces — `GeneralCommandLine` handles quoting automatically.

## File Conventions

- Hand-written code: `src/main/kotlin/`
- Generated code: `src/main/gen/` (gitignored, regenerated on build)
- Grammar sources: `grammars/Antlers.flex`, `grammars/Antlers.bnf`
- Plugin manifest: `src/main/resources/META-INF/plugin.xml`
- Optional dependency config: `src/main/resources/META-INF/antlers-php.xml`
- Color schemes: `src/main/resources/colorSchemes/`
- Bundled LSP server: `src/main/resources/language-server/antlersls.js`
- Intention descriptions: `src/main/resources/intentionDescriptions/`
- Tag parameter data: `src/main/kotlin/com/antlers/support/statamic/StatamicTagParameters.kt` (hand-maintained)
- Scope variable data: `src/main/kotlin/com/antlers/support/statamic/StatamicScopeVariables.kt` (hand-maintained)
- Block tag registry: `src/main/kotlin/com/antlers/support/AntlersBlockTags.kt`
- Storage conversion service: `src/main/kotlin/com/antlers/support/statamic/StatamicStorageConversionService.kt`
- Storage conversion dialogs: `src/main/kotlin/com/antlers/support/settings/StatamicStorageConversionDialogs.kt`

## Core Architecture

### Dual PSI Tree

Antlers files contain both Antlers expressions (`{{ }}`) and HTML/CSS/JS, so the plugin uses IntelliJ's Template Language Framework with `MultiplePsiFilesPerDocumentFileViewProvider`.

Each file maintains two PSI trees:

- **Antlers tree** (base language): tags, expressions, conditionals, modifiers
- **HTML tree** (template data language): full HTML/CSS/JS intelligence

`AntlersFileViewProvider` overrides `findElementAt()` and `findReferenceAt()` to prefer the HTML tree for template data regions and fall back to the Antlers tree.

The `TemplateDataElementType` outer fragment must use `OuterLanguageElementType`, not a plain PSI element type. Using the wrong type causes `Wrong element created by ASTFactory` when switching files or reparsing.

### Grammar and Code Generation

Two Grammar-Kit grammars in `grammars/` generate code into `src/main/gen/`:

1. `Antlers.flex` (JFlex) → `_AntlersLexer.java`
2. `Antlers.bnf` (Grammar-Kit BNF) → `AntlersParser.java` plus PSI element types/interfaces/implementations

Lexer states: `YYINITIAL`, `ANTLERS_EXPR`, `COMMENT`, `PHP_RAW`, `PHP_ECHO`, `DQ_STRING`, `SQ_STRING`, `NOPARSE`.

### Token Identity Bridge

The parser and lexer must share the same `IElementType` instances. `AntlersTokenTypes.factory()` uses reflection to resolve BNF token names to existing lexer token fields. The BNF `tokens` block must list bare token names, not string values, so the factory receives names like `ANTLERS_OPEN` instead of display strings like `{{`.

### Grammar Patterns

**Adding a new keyword:**

1. Add a lexer rule in `Antlers.flex` with lookahead: `"keyword" / [^a-zA-Z0-9_]`
2. Add a token field in `AntlersTokenTypes.kt`: `@JvmField val KEYWORD_X = AntlersTokenType("KEYWORD_X")`
3. Add the token to `AntlersTokenSets.KEYWORDS`
4. Add the bare token name to the `tokens` block in `Antlers.bnf` and reference it in grammar rules
5. Run `./gradlew build` to regenerate everything

**BNF error recovery:**

- `pin=1` means "commit after matching the first token" and prevents backtracking.
- `recoverWhile` skips tokens until a predicate matches. Do not apply it to rules that can fail cleanly without consuming tokens.
- `private` rules do not generate PSI nodes and are useful for dispatch/grouping.
- If a grammar rule accepts only `IDENTIFIER` but the lexer produces a keyword token (e.g. `KEYWORD_IF` for `if`), the fix is a private `tagNameAtom` rule that lists `IDENTIFIER | KEYWORD_IF | KEYWORD_UNLESS | ...`. This is preferable to making keywords context-sensitive in the lexer.

**Closing-tag grammar fix:** `{{ /if }}` requires a private `tagNameAtom` rule in `Antlers.bnf` that accepts keyword tokens. After that change, `{{ /if }}` parses as `closingTag`, so `AntlersFoldingBuilder` must remap `if → COND_IF` and `unless → COND_UNLESS` to match the stack keys used by opening conditionals.

## Editor Features

### Highlighting

`AntlersEditorHighlighter` extends `LayeredLexerEditorHighlighter`:

- Base layer: `AntlersSyntaxHighlighter`
- Layer for `TEMPLATE_TEXT`: HTML/CSS/JS syntax highlighter

### Semantic Highlighting

`AntlersHighlightingAnnotator` colors all identifier parts within `AntlersTagName` nodes. It uses `isTagLike()` to distinguish real tags (namespaced, parameterized, closing, or known block tags) from simple variables like `{{ title }}`.

Partial paths (`partial:components/hero`) get the `TAG_PATH` text attribute with underline, signaling they are navigable. Both Darcula and Default color schemes define `ANTLERS_TAG_PATH` with `EFFECT_TYPE=1` (underline).

### Alpine.js Integration

Implemented with `AntlersAlpineAttributeInjector` (MultiHostInjector), `AntlersAlpineReferenceContributor`, and `AntlersAlpineReferenceResolver`.

Rules:

- `x-data` is injected as a JS expression wrapped with `(` `)` so object literals parse correctly.
- Event-style attributes (`@click`, `x-on:*`, `x-init`, `x-effect`) are injected as statements.
- `x-for` is **not** injected as JS because Alpine's `(item, index) in items` syntax is invalid JS. Loop aliases are resolved manually in the reference resolver.
- Cmd-click on Alpine method calls resolves through PSI references first. `AntlersGotoDeclarationHandler` is a fallback for Antlers-specific navigation.
- `AntlersAlpineReferenceContributor` must stay cheap — do not eagerly call the full resolver from `getReferencesByElement()`.
- `AntlersAlpineReferenceResolver` caches the injected `x-data` object literal lookup per `XmlAttributeValue`. Do not re-materialize injected PSI while walking ancestors.
- `AntlersAlpineXmlSuppressionProvider` suppresses false XML/HTML warnings on Alpine attributes (`:`, `@`, `x-` prefixes). When adding new Alpine-like prefixes, update `isAlpineAttribute()`.

### Auto-closing Delimiters

`AntlersTypedHandler` auto-closes `{{ }}` when the user types `{{`, producing `{{  }}` with the cursor between the spaces. Toggled by `AntlersSettings.enableAutoCloseDelimiters`.

- Any feature that generates Antlers output (live templates, snippets, intentions) must account for the `}}` already present after the cursor.
- The handler checks for existing `}}` to avoid double-closing and removes stray `}` from IntelliJ's single-brace pairing.

### PHP Injection

PHP intelligence inside `{{? ?}}` (raw) and `{{$ $}}` (echo) blocks is implemented via `AntlersPhpInjector`, registered as an optional dependency on `com.jetbrains.php` in `antlers-php.xml`.

- `{{? ... ?}}` → prefix `<?php `, suffix ` ?>`
- `{{$ ... $}}` → prefix `<?php echo `, suffix `; ?>`
- The BNF rules use `AntlersPhpBlockMixin`, which implements `PsiLanguageInjectionHost`.

### Formatting

Formatting uses `TemplateLanguageFormattingModelBuilder`. `AntlersFormattingModelBuilder` must special-case `OuterLanguageElementType` nodes and delegate them back to `SimpleTemplateLanguageFormattingModelBuilder`.

`AntlersBlock` enforces spacing: one space inside `{{ }}`, one space around operators, no space around `=` (parameters), `:` (modifier args), or `/` (paths).

`Reformat Code` uses two layers:

- `AntlersFormattingModelBuilder` / `AntlersBlock` for token spacing
- `AntlersConditionalPostFormatProcessor` for standalone Antlers control/tag line indentation

The post-format processor uses a structural depth stack over both standalone Antlers and HTML lines. Only real nesting constructs (HTML parents, `if`/`unless`/`switch`/`else`/`elseif`) create indentation. Flat sequences of standalone tags (e.g. consecutive `{{ partial:... }}`) must stay aligned.

**`OP_DIVIDE` spacing:** Use `.around(OP_DIVIDE).none()`. In Antlers, `/` is usually a path separator. `.none()` actively removes spaces; omitting the rule returns `null` and leaves whitespace unchanged.

### Code Folding

`AntlersFoldingBuilder` uses a stack to match opening/closing tags in document order (the PSI tree is flat). Regular tags use `tagName.text` as stack keys; conditionals use synthetic keys `COND_IF`/`COND_UNLESS` to avoid collisions. `getPlaceholderText()` shows the expression text, truncated at 60 characters.

### Auto-close Tag on `/`

When the user types `{{ /`, `AntlersTypedHandler` scans backward via regex over raw document text to find the nearest unclosed block tag and auto-completes the name. Only tags in `AntlersBlockTags.NAMES` are considered.

### Auto-indent on Enter

`AntlersEnterHandler.postProcessEnter()` adds one indent level after block tag openers (via `AntlersBlockTags.isBlockTag()`).

### Block Tag Registry

`AntlersBlockTags` is a shared `Set<String>` of tag names that accept a closing `{{ /tag }}` pair. Used by the post-format processor, enter handler, typed handler, structure view, and completion contributor. When adding a new block tag, add it to `AntlersBlockTags.NAMES` and all consumers benefit automatically.

### Antlers Language Server (LSP)

The plugin integrates the [Stillat Antlers Language Server](https://github.com/Stillat/vscode-antlers-language-server) for formatting and diagnostics. Bundled at `src/main/resources/language-server/antlersls.js`.

- `AntlersLspServerSupportProvider` triggers on `.antlers.html`/`.antlers.php` files
- `AntlersLspServerDescriptor` extracts the JS to a temp directory and launches via `node --stdio`
- Registered directly in `plugin.xml` (not via optional dependency). **Do not use `<depends>` with `intellij.platform.lsp`** — it's not a valid plugin ID.
- Native PSI is the source of truth for completion, hover, go-to-definition. LSP provides formatting and diagnostics only.
- LSP completion is disabled via `LspCompletionSupport` to avoid duplicating `AntlersCompletionContributor`.
- The extracted server is patched to remove the VS Code-specific `antlers/projectDetailsAvailable` request.
- Requires Node.js (found via Herd, Homebrew, or PATH).

To rebuild from upstream:
```bash
cd /tmp && git clone --depth 1 https://github.com/Stillat/vscode-antlers-language-server.git antlers-lsp
cd antlers-lsp && npm install && npm run bundle:antlersls
cp antlersls/server.js /path/to/antlers-support/src/main/resources/language-server/antlersls.js
```

## Data, Completion, and Documentation

### Statamic Catalog and Hover Docs

Official Statamic tags, modifiers, and variables are generated (not hand-maintained).

- Source of truth: `scripts/generate_statamic_catalog.py`
- Generated output: `src/main/kotlin/com/antlers/support/statamic/StatamicCatalogGenerated.kt`
- Runtime lookup: `StatamicCatalog`

Powers completion (`StatamicData`, `AntlersCompletionContributor`), hover docs (`AntlersDocumentationProvider`), and official descriptions/examples/URLs.

### Completion Pre-building

`StatamicData` keeps lazy, pre-built `LookupElement` lists (`TAG_ELEMENTS`, `MODIFIER_ELEMENTS`, `VARIABLE_ELEMENTS`, `SUB_TAG_ELEMENT_MAP`). Always call `result.addAllElements(StatamicData.XXX_ELEMENTS)` — do not build `LookupElement` instances per keystroke.

### Completion Auto-popup

`AntlersCompletionContributor.invokeAutoPopup()` returns `true` for `:` so typing `{{ partial:` or `{{ collection:` immediately shows the popup.

### Tag Parameter Completion

`StatamicTagParameters` (hand-maintained) holds parameter metadata for common Statamic tags. `StatamicData.getParameterElements(tagName)` builds `LookupElement` instances with an `InsertHandler` that appends `=""` and positions the caret. Required parameters render bold. The completion contributor detects parameter context via `PsiTreeUtil.getParentOfType()` and filters already-used names.

### Scope-Aware Variable Completion

`StatamicScopeVariables` defines variables available inside tag pair loops (loop variables like `first`/`last`/`count`, plus tag-specific fields). The completion contributor detects scope by walking the flat PSI tree with a stack to find the nearest enclosing block tag.

### Collection Handle Discovery

`StatamicProjectCollections` is a project-level service that discovers collection handles and other Statamic resources from both flat-file and Eloquent sources. It also indexes blueprint entry fields for collection-scoped completion.

- **Flat-file driver**: scans `content/collections/`, `content/navigation/`, `content/taxonomies/`, etc.
- **Eloquent driver**: runs `php artisan tinker --execute` to query Statamic Facades
- **Entry fields**: blueprint YAML is scanned locally for both drivers
- **Driver detection**: reads `config/statamic/eloquent-driver.php`, checks if `collections.driver` is `'eloquent'`
- **Background execution**: uses `Task.Backgroundable` — never run external processes inside ReadAction
- **Auto-index**: optional VFS file watcher with 2-second debounce

## Storage Conversion

`StatamicStorageConversionService` converts Statamic sites between flat-file and Eloquent (database) storage. It delegates to `StorageConversionEngine`.

### Architecture

- **Service** (`StatamicStorageConversionService`): project-level facade with 10-second `StorageSnapshot` cache. Exposes `analyze()`, `convert()`, `currentStorageOverview()`.
- **Engine** (`StorageConversionEngine`): stateless worker for snapshot capture, analysis, validation, backup, and conversion. Accepts a `CommandExecutor` interface for testability.
- **Dialogs** (`StatamicStorageConversionDialogs.kt`): `StorageConversionConfirmationDialog` (pre-conversion analysis) and `StorageConversionProgressDialog` (live progress).

Key data types:

- **`StorageSnapshot`**: resource counts/metrics for a driver at a point in time
- **`StorageConversionAnalysis`**: source/target snapshots, conflicts, supported resolutions (MERGE/OVERWRITE/CANCEL)
- **`StorageConversionRequest`**: target driver, conflict resolution, optional `DatabaseConnectionConfig`

Conversion phases: ANALYZING → VALIDATING → BACKUP → MIGRATING → VERIFYING → COMPLETE.

Backups go to `storage/statamic-toolkit/backups/`. Logs go to `storage/logs/statamic-toolkit-conversion-{timestamp}.log`. Concurrent conversions are prevented via `storage/statamic-toolkit/conversion.lock` (`FileChannel.lock()`).

The Data Source settings page shows contextual buttons — "Convert to database" (with `DatabaseConfigDialog` for `.env` setup) when on flat-file, "Convert to flat file" when on Eloquent.

### Artisan Command Flags

Many `eloquent:export-*` and `eloquent:import-*` commands have `confirm()` prompts that silently return `false` under `--no-interaction`, causing the command to skip all work while printing a success message. Commands that support `--force` must always receive it. The `COMMANDS_WITH_FORCE` set tracks which commands accept it; commands not in this set (e.g. `export-entries`, `export-sites`) will reject the flag.

### Critical vs Non-critical Commands

`CRITICAL_EXPORT_COMMANDS` and `CRITICAL_IMPORT_COMMANDS` (`export-collections`, `export-entries` and their import counterparts) are fatal on failure. All other commands are wrapped in try-catch — failures from missing tables or binding exceptions in partially-set-up projects are logged and execution continues.

### Orphaned Entry Cleanup

Before `eloquent:export-entries`, the engine runs an orphan cleanup via `artisan tinker` using `Collection::findByHandle()` (Statamic's own facade) to match exactly what `export-entries` does internally. Entries referencing unresolvable collections are deleted. The `analyze()` step also counts orphans and surfaces a warning.

### Driver Config Rewrite

The `eloquent-driver.php` config uses `env()` and `\Statamic\...\Model::class` constants, so it cannot be `require`-ed from bare `php -r`. The rewrite runs via `artisan tinker` with a `preg_replace` on `'driver' => '...'` patterns, preserving the original file structure.

### Post-conversion UI Refresh

After a successful conversion, three things happen:

1. **Immediate UI update**: the `onFinished` callback sets the driver label and button visibility directly from the conversion target
2. **VFS refresh**: `refreshCollections()` calls `projectDir?.refresh(true, true)` so the flat-file scanner sees externally-created files
3. **Background re-index**: `StatamicProjectCollections.refresh()` re-detects the driver and re-indexes; `notifyListeners()` uses `ModalityState.any()` so listeners dispatch during modal dialogs

### Verification

Post-conversion verification compares source and target snapshots. Mismatches are returned as informational messages, not thrown as exceptions. This prevents partial exports (from non-critical command failures) from triggering a full rollback.

### Validation Resilience

After converting to flat-file, Statamic may fail to boot via `artisan` (e.g. null site in `Cascade` if sites data wasn't exported). The `analyze()` step must handle this gracefully:

- If `availablePleaseCommands()` returns empty, skip command availability checks with a warning instead of blocking.
- `runMigrationDryRun()` failures are warnings, not errors — the actual `artisan migrate` runs during conversion.
- `captureDatabaseSnapshot()` failures when inspecting the target return an empty snapshot — the tables get created during conversion via `artisan migrate`.

### Dialog Rules

- **Do not start execution from a `DialogWrapper` constructor or `init` block.** The modal event loop hasn't started, so UI updates queue up and never dispatch. Use a `windowOpened` listener.
- Both convert buttons are disabled while commands run to prevent duplicate threads.
- The `CommandExecutor` interface allows tests to substitute a mock executor. See `StatamicStorageConversionServiceTest.kt`.

## IDE Integration Patterns

### Settings

Feature toggles live in `AntlersSettings` (`PersistentStateComponent`), exposed at Settings > Languages & Frameworks > Statamic.

To add a new toggle:

1. Add `var enableXxx: Boolean = true` to `AntlersSettings.State`
2. Add a `JBCheckBox` in the appropriate configurable
3. Wire through `isModified`, `apply`, `reset`
4. Guard the feature entry point with `if (!AntlersSettings.getInstance().state.enableXxx) return`

#### Nested Settings Sub-pages

Sub-pages (Data Source, Editor, Completion, Navigation & Documentation, Language Injection) are separate `Configurable` classes registered in `plugin.xml` with `parentId="com.statamic.toolkit.settings"`.

**Important**: `Configurable.Composite` alone does NOT create tree children. Each child must be registered as an `applicationConfigurable` with the correct `parentId`.

To navigate within an open settings dialog, walk the Swing hierarchy to find `SettingsEditor` and call `editor.select(configurable)`. Do **not** use `ShowSettingsUtil.showSettingsDialog()` from within a settings page — it opens a second dialog.

#### Settings Binding Pattern

Each sub-page uses `CheckboxField(box, read, write)` to bind checkboxes to `AntlersSettings.State`. The `fields` list is built once; `isModified`, `apply`, and `reset` collapse to `any`/`forEach` over it.

### Statamic Menu

Top-level **Statamic** menu for PHP-side workflows. Content query snippets are enabled only in PHP files. Generators create files under `app/Http/Controllers`, `app/Tags`, `app/Modifiers` and open existing files instead of overwriting. Menu visibility is handled by `StatamicProjectActionGroup` and `StatamicPhpInsertActionGroup`.

### Go-to Custom Tag/Modifier Definition

`AntlersGotoDeclarationHandler` resolves unknown tags → `app/Tags/ClassName.php` and unknown modifiers → `app/Modifiers/ClassName.php`. Built-in tags and block tags are skipped. File lookup uses `FilenameIndex` with a directory-scoped `GlobalSearchScope`. Partial navigation is handled by the LSP server.

Known limitation: tags with `protected static $handle = 'custom_name'` decouple the class name from the tag name; filename-based lookup will miss these.

### Extract Partial Intention

`ExtractPartialIntention` extracts selected text to `resources/views/partials/{name}.antlers.html` and replaces it with `{{ partial:{name} }}`. Available when the file is `.antlers.html`/`.antlers.php` and the editor has a selection.

### Status Bar Widget

`StatamicStatusBarWidgetFactory` shows a popup with driver type, indexing status, resource counts, an auto-index checkbox, and a refresh button. Uses `JBPopupFactory.createComponentPopupBuilder()`.

### Structure View Nesting

`AntlersStructureViewElement` builds a virtual nested tree from the flat PSI by matching opening/closing pairs with a stack. HTML landmark elements (`<header>`, `<main>`, `<nav>`, etc.) are merged by document offset.

### Navigation Bar

`AntlersStructureAwareNavbar` extends `StructureAwareNavBarModelExtension`. Use `override val language: Language`, not `override fun getLanguage()`. `getIcon()` does not exist on the superclass.

### Find Usages and Symbols

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

### Release Notes

Plugin update notes come from `CHANGELOG.md`, not `<change-notes>` in `plugin.xml`. `build.gradle.kts` extracts version sections and feeds them into `pluginConfiguration.changeNotes`. Verify the generated block in `build/tmp/patchPluginXml/plugin.xml`.

### Release Process

Every version bump must update:

1. `pluginVersion` in `gradle.properties`
2. A new `## [x.y.z]` section at the top of `CHANGELOG.md`
3. `README.md` features list and roadmap if the release adds visible features
4. `CLAUDE.md` if the release adds new subsystems or patterns
