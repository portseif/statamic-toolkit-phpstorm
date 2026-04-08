# CLAUDE.md

This file provides guidance to Claude Code (`claude.ai/code`) when working in this repository.

## Project Overview

**Statamic Toolkit** is a JetBrains IDE plugin for the [Antlers](https://statamic.dev/frontend/antlers) template language in Statamic CMS.

- Marketplace/plugin display name: **Statamic Toolkit**
- Language name: **Antlers**
- Current development target: **PhpStorm** (`platformType=PS` in `gradle.properties`)
- Language-level registrations (file type, parser, highlighter, color scheme, code style) use **Antlers**
- The settings panel under Languages & Frameworks uses **Statamic**

## Build and Packaging

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

**Important**: `<depends>` only accepts **plugin IDs** (e.g. `com.jetbrains.php`, `JavaScript`), not IntelliJ v2 module names (e.g. `intellij.platform.lsp`). Attempting to use a module name as a `<depends>` target will silently fail — the optional config file never loads and services registered there are never available. For platform APIs that are part of the core (like the LSP API in 2025.1+), register extensions directly in `plugin.xml` instead of using an optional dependency.

## Core Architecture

### Dual PSI Tree

Antlers files contain both Antlers expressions (`{{ }}`) and HTML/CSS/JS, so the plugin uses IntelliJ's Template Language Framework with `MultiplePsiFilesPerDocumentFileViewProvider`.

Each file maintains two PSI trees:

- **Antlers tree** (base language): tags, expressions, conditionals, modifiers
- **HTML tree** (template data language): full HTML/CSS/JS intelligence

`AntlersFileViewProvider` overrides `findElementAt()` and `findReferenceAt()` to prefer the HTML tree for template data regions and fall back to the Antlers tree. This enables CSS class navigation, JS references, and similar editor features inside template files.

The `TemplateDataElementType` outer fragment must use `OuterLanguageElementType`, not a plain PSI element type. Using the wrong outer element causes the Template Language Framework to throw `Wrong element created by ASTFactory` when switching files or reparsing mixed Antlers/HTML content.

### Grammar and Code Generation

Two Grammar-Kit grammars in `grammars/` generate code into `src/main/gen/`:

1. `Antlers.flex` (JFlex) generates `_AntlersLexer.java`
2. `Antlers.bnf` (Grammar-Kit BNF) generates `AntlersParser.java` plus PSI element types/interfaces/implementations

The lexer is stateful and uses these states:

- `YYINITIAL`
- `ANTLERS_EXPR`
- `COMMENT`
- `PHP_RAW`
- `PHP_ECHO`
- `DQ_STRING`
- `SQ_STRING`
- `NOPARSE`

### Token Identity Bridge

The parser and lexer must share the same `IElementType` instances.

`AntlersTokenTypes.factory()` uses reflection to resolve BNF token names to existing lexer token fields. The BNF `tokens` block must list bare token names, not string values, so the factory receives names like `ANTLERS_OPEN` instead of display strings like `{{`.

## Editor Features

### Highlighting

`AntlersEditorHighlighter` extends `LayeredLexerEditorHighlighter`:

- Base layer: `AntlersSyntaxHighlighter`
- Layer for `TEMPLATE_TEXT`: HTML/CSS/JS syntax highlighter

Antlers-specific semantic colors such as tag names and parameter names come from `AntlersHighlightingAnnotator`, not from the lexer highlighter alone.

### Alpine.js Integration

Alpine support is implemented with:

- `AntlersAlpineAttributeInjector` (`MultiHostInjector`)
- `AntlersAlpineReferenceContributor`
- `AntlersAlpineReferenceResolver`

Rules:

- `x-data` is injected as a JS expression and wrapped with `(` `)` so object literals parse correctly.
- Event-style attributes such as `@click`, `x-on:*`, `x-init`, and `x-effect` are injected as statements.
- `x-for` is **not** injected as JavaScript because Alpine's `(item, index) in items` syntax is not valid JS and creates parser noise.
- `x-for` loop aliases are resolved manually in the Alpine reference resolver so descendant expressions can still navigate and avoid false unresolved warnings.
- Cmd-click on Alpine method calls should resolve through normal PSI references first. `AntlersGotoDeclarationHandler` remains a fallback for Antlers-specific navigation such as partials.
- `AntlersAlpineReferenceContributor` must stay cheap. Do not eagerly call the full resolver from `getReferencesByElement()`.
- `AntlersAlpineReferenceResolver` caches the injected `x-data` object literal lookup per `XmlAttributeValue`. Do not re-materialize injected PSI while walking ancestors.
- `AntlersAlpineXmlSuppressionProvider` suppresses false XML/HTML warnings on Alpine attributes. It handles multiple inspection IDs (`XmlUnboundNsPrefix`, `HtmlUnknownAttribute`, `HtmlUnknownTag`, `XmlUnresolvedReference`, `CheckTagEmptyBody`) and matches any attribute starting with `:`, `@`, or `x-`. When adding support for new Alpine-like attribute prefixes, update `isAlpineAttribute()` in this class.

### Auto-closing Delimiters

`AntlersTypedHandler` auto-closes `{{ }}` when the user types `{{`. The handler inserts `  }}` (two spaces + closing braces) and places the caret between the spaces, so the document becomes `{{ | }}` with the cursor in the middle.

Rules:

- When auto-close is enabled (the default), typing `{{` always produces `{{  }}` with the cursor between the two spaces. Any feature that generates Antlers output (live templates, snippets, intentions) must account for the `}}` already being present after the cursor.
- The handler checks for an existing `}}` immediately after the caret to avoid double-closing.
- The handler also removes a stray `}` left by IntelliJ's built-in single-brace pairing before inserting its own `  }}`.
- The feature is toggled by `AntlersSettings.enableAutoCloseDelimiters`.

### PHP Injection

PHP intelligence inside `{{? ?}}` (raw PHP) and `{{$ $}}` (echo PHP) blocks is implemented via `AntlersPhpInjector`, a `MultiHostInjector` registered as an optional dependency on `com.jetbrains.php`.

Rules:

- The BNF rules for `phpRawBlock` and `phpEchoBlock` use `AntlersPhpBlockMixin`, which implements `PsiLanguageInjectionHost`.
- `{{? ... ?}}` content is injected with prefix `<?php ` and suffix ` ?>`.
- `{{$ ... $}}` content is injected with prefix `<?php echo ` and suffix `; ?>`.
- The injector is registered in `antlers-php.xml`, loaded via `<depends optional="true" config-file="antlers-php.xml">com.jetbrains.php</depends>`.
- Formatting inside PHP blocks follows the user's PHP code style settings.

### Formatting

Formatting uses `TemplateLanguageFormattingModelBuilder`, not a plain formatting model builder.

`AntlersFormattingModelBuilder` must special-case `OuterLanguageElementType` nodes and delegate them back to `SimpleTemplateLanguageFormattingModelBuilder`. Otherwise mixed-template formatting breaks around template data boundaries.

`AntlersBlock` (a `TemplateLanguageBlock`) holds a `SpacingBuilder` that enforces token-level spacing rules:

- One space inside `{{ }}` delimiters
- One space around operators
- No space around `=` in parameters
- No space around `:` in modifier args
- No space around `/`

The `SpacingBuilder` is instantiated once per block in the constructor, and `getSpacing()` is called before the super-class fallback.

`Reformat Code` uses two layers:

- `AntlersFormattingModelBuilder` / `AntlersBlock` for token spacing
- `AntlersConditionalPostFormatProcessor` for standalone Antlers control/tag line indentation after the normal formatter runs

The post-format processor exists because the template-language formatter alone cannot reliably align `{{ if }}` / `{{ else }}` / `{{ /if }}` in mixed Antlers/HTML files.

`AntlersConditionalPostFormatProcessor` now uses a structural depth stack over both standalone Antlers lines and standalone HTML lines. It treats single-line HTML open/close tags and single-line standalone Antlers tags as one nesting model, so surrounding HTML parents (`<div>`, `<main>`, `<header>`, etc.) and Antlers control-flow lines indent consistently in the same pass.

The processor should only create extra indentation for real nesting constructs:

- HTML parent structure
- Antlers control-flow constructs such as `if`, `unless`, `switch`, `else`, and `elseif`

Flat sequences of standalone tags such as consecutive `{{ partial:... }}` lines must stay aligned with each other. Do not let a previously-added continuation indent cascade through sequential standalone tags.

#### Known formatting limitations and rules

**`{{ else }}` / `{{ elseif }}` alignment**  
The template-language formatter alone cannot align these tags correctly in mixed Antlers/HTML files because they are often merged under `DataLanguageBlockWrapper` nodes that carry surrounding HTML indentation. The plugin handles the common `Reformat Code` case with `AntlersConditionalPostFormatProcessor`, which realigns standalone Antlers control/tag lines after formatting. If you need richer indentation for arbitrary mixed HTML content between branches, that would still require a more custom formatting model.

**Standalone-line scope**  
`AntlersConditionalPostFormatProcessor` intentionally operates on lines where the trimmed line text is exactly one standalone Antlers tag, plus simple standalone HTML open/close/self-closing lines. It is designed for `Reformat Code` cleanup of mixed Antlers/HTML structure, not arbitrary inline HTML fragments or multi-node line layouts.

**`OP_DIVIDE` spacing**  
Use `.around(OP_DIVIDE).none()` in the `SpacingBuilder`. Do not use `.spaces(1)`, and do not omit the rule. In Antlers, `/` is more often a path separator such as `partial:partials/sections/hero` than an arithmetic operator. `.none()` actively removes spaces during Reformat Code; omitting the rule returns `null` and leaves whitespace unchanged.

**Closing-tag grammar fix**  
`{{ /if }}` used to fail because `tagName` accepted only `IDENTIFIER`, while `if` is lexed as `KEYWORD_IF`. The fix is a private `tagNameAtom` rule in `Antlers.bnf` that accepts `IDENTIFIER | KEYWORD_IF | KEYWORD_UNLESS | KEYWORD_SWITCH`. After that change, `{{ /if }}` parses as `closingTag` instead of `conditionalTag`, so `AntlersFoldingBuilder` must map `if -> COND_IF` and `unless -> COND_UNLESS` to match the stack keys used by opening conditionals.

### Code Folding

`AntlersFoldingBuilder` extends `FoldingBuilderEx` and implements `DumbAware`.

It uses a stack of `OpenFold(tag, key)` entries to match opening and closing tags in document order. The Antlers PSI tree is flat, so walking `root.children` with a stack is the only viable matching strategy.

Rules:

- Regular tag pairs use `tagExpr.tagName.text` as the stack key.
- Conditional pairs use synthetic keys `COND_IF` and `COND_UNLESS` to avoid collisions with real tags named `if`.
- Because `{{ /if }}` and `{{ /unless }}` parse as `closingTag`, the closing branch remaps `if -> COND_IF` and `unless -> COND_UNLESS` before stack lookup.
- `getPlaceholderText()` should show the full expression/condition text, trimmed and truncated at 60 characters, so folded blocks read like `{{ if site:environment === 'production' }}...`.

### Auto-close Tag on `/`

`AntlersTypedHandler` also handles `/` typed inside `{{ }}`. When the user types `{{ /`, it scans backward through the document text to find the nearest unclosed block tag and auto-completes the closing tag name.

The scan uses a stack: closing tags push, opening block tags pop or return. Only tags in `AntlersBlockTags.NAMES` are considered block tags. The scan uses a regex over raw document text, not PSI traversal, for performance.

### Auto-indent on Enter

`AntlersEnterHandler.postProcessEnter()` checks if the previous line was a block tag opener (via `AntlersBlockTags.isBlockTag()`). If so, it adds one indent level to the new line. The indent unit is read from `CodeStyleSettingsManager`.

### Block Tag Registry

`AntlersBlockTags` (in `src/main/kotlin/com/antlers/support/AntlersBlockTags.kt`) is a shared `Set<String>` of tag names known to accept a closing `{{ /tag }}` pair. It is used by:

- `AntlersConditionalPostFormatProcessor` — indents content inside block tag pairs
- `AntlersEnterHandler` — auto-indents after block tag openers
- `AntlersTypedHandler` — auto-closes `{{ /` with the nearest unclosed block tag
- `AntlersStructureViewElement` — nests children under block tag openers
- `AntlersCompletionContributor` — scope-aware variable detection

When adding a new known block tag, add it to `AntlersBlockTags.NAMES` and all consumers benefit automatically.

### Antlers Language Server (LSP)

The plugin integrates the [Stillat Antlers Language Server](https://github.com/Stillat/vscode-antlers-language-server) for formatting and diagnostics. The compiled server JS is bundled at `src/main/resources/language-server/antlersls.js` (1.1MB, built with esbuild from the Stillat repo).

Architecture:

- `AntlersLspServerSupportProvider` triggers the server when `.antlers.html`/`.antlers.php` files open
- `AntlersLspServerDescriptor` extracts the bundled JS to a temp directory and launches it via `node --stdio`
- The LSP server and `AntlersLspStatusService` are registered directly in `plugin.xml`, not via an optional dependency. The LSP API (`intellij.platform.lsp`) is part of the core platform in PhpStorm 2025.1+, so no `<depends>` guard is needed. **Do not use `<depends optional="true">com.intellij.modules.lsp</depends>` or `<depends>intellij.platform.lsp</depends>`** — neither is a valid plugin ID. The `<depends>` tag only works with plugin IDs (like `com.jetbrains.php`), not v2 module names.
- Native PSI is the source of truth for completion, hover, go-to-definition, partial navigation, and other Antlers-aware editor features. LSP should be used for formatting and diagnostics.
- LSP completion is intentionally disabled via a custom `LspCompletionSupport` override because mixing LSP completion with `AntlersCompletionContributor` creates duplicate suggestions.
- LSP hover and go-to-definition are disabled (native PSI handles those); LSP formatting and diagnostics are enabled
- The extracted temp server copy is written as `antlersls-statamic-toolkit.js` and patched to remove the VS Code-specific `antlers/projectDetailsAvailable` request. Without that patch, the server starts and then disconnects unexpectedly under JetBrains' generic LSP client.
- Requires Node.js on the user's machine; finds it via Herd path, Homebrew, or system PATH

To rebuild the bundled server from upstream:
```bash
cd /tmp && git clone --depth 1 https://github.com/Stillat/vscode-antlers-language-server.git antlers-lsp
cd antlers-lsp && npm install && npm run bundle:antlersls
cp antlersls/server.js /path/to/antlers-support/src/main/resources/language-server/antlersls.js
```

### Semantic Highlighting

`AntlersHighlightingAnnotator` colors all identifier parts within `AntlersTagName` nodes, not just the head. It uses `isTagLike()` to distinguish real tags (namespaced, parameterized, closing, or known block tags via `AntlersBlockTags.isBlockTag()`) from simple variables like `{{ title }}`.

Partial paths (`partial:components/hero`) get the `TAG_PATH` text attribute which adds an underline effect, signaling they are navigable. The `:` and `/` separators within tag names are also colored.

Both Darcula and Default color schemes define `ANTLERS_TAG_PATH` with `EFFECT_TYPE=1` (underline) matching the tag name foreground color.

## Data, Completion, and Documentation

### Statamic Catalog and Hover Docs

Official Statamic tags, modifiers, and variables are generated rather than maintained by hand.

- Source of truth: `scripts/generate_statamic_catalog.py`
- Generated output: `src/main/kotlin/com/antlers/support/statamic/StatamicCatalogGenerated.kt`
- Runtime lookup layer: `StatamicCatalog`

This catalog powers:

- Antlers completion (`StatamicData`, `AntlersCompletionContributor`)
- Hover and quick documentation (`AntlersDocumentationProvider`)
- Official descriptions, examples, and URLs for tags, modifiers, and variables

Variable-vs-tag hover resolution is intentionally conservative:

- Simple bare identifiers with no parameters can resolve as variables
- Namespaced forms such as `nav:foo` and `current_user:email` fall back to root tag/variable handles as needed

### Completion Pre-building

`StatamicData` keeps these as lazy, pre-built structures:

- `TAG_ELEMENTS`
- `MODIFIER_ELEMENTS`
- `VARIABLE_ELEMENTS`
- `SUB_TAG_ELEMENT_MAP`

`AntlersCompletionContributor` must call `result.addAllElements(StatamicData.XXX_ELEMENTS)`. Do not revert to building `LookupElement` instances per keystroke.

### Tag Parameter Completion

`StatamicTagParameters` (hand-maintained) holds parameter metadata for ~14 common Statamic tags. Each tag maps to a list of `TagParameter(name, description, required)`.

`StatamicData.getParameterElements(tagName)` builds `LookupElement` instances with an `InsertHandler` that appends `=""` and places the caret between the quotes. Required parameters render bold.

The completion contributor detects parameter context by finding the parent `AntlersTagExpression` via `PsiTreeUtil.getParentOfType()`, then filters out already-used parameter names from `tagExpression.parameterList`.

When updating parameter data for a tag, verify against the official Statamic docs (e.g. `https://statamic.dev/tags/collection`).

### Scope-Aware Variable Completion

`StatamicScopeVariables` defines variables available inside tag pair loops:

- **Loop variables** (`first`, `last`, `count`, `index`, `total_results`, etc.) — available in all tag pair contexts
- **Tag-specific fields** — `collection` gets built-in entry fields (`title`, `slug`, `url`, `date`, etc.) plus indexed blueprint fields from `StatamicProjectCollections`, `nav` gets nav fields (`is_current`, `children`, `depth`), `taxonomy`/`search`/`assets`/`form` each have their own

The completion contributor detects scope by walking the flat Antlers PSI tree with a stack (similar to the folding builder) to find the nearest enclosing unclosed block tag. For collection scopes, it should preserve both the tag name and the raw tag text so `collection:posts` and `collection from="posts"` can both resolve collection-specific blueprint fields.

### Collection Handle Discovery

`StatamicProjectCollections` is a project-level service that discovers collection handles (and other Statamic resources) from both flat-file and Eloquent driver sources. It also indexes collection entry fields from `resources/blueprints/collections` so completion can surface real project fields inside collection loops.

Rules:

- **Default (flat-file) driver**: scans `content/collections/`, `content/navigation/`, `content/taxonomies/`, etc. for directories and YAML files
- **Eloquent driver**: runs `php artisan tinker --execute` to query all Statamic Facades in a single call
- **Entry fields**: collection blueprint YAML is scanned locally for both drivers; Eloquent still uses blueprint files for field discovery because the field schema lives in blueprints, not entry records
- **Driver detection**: reads `config/statamic/eloquent-driver.php` and checks if `collections.driver` is `'eloquent'`
- **Background execution**: uses `ProgressManager.getInstance().run(Task.Backgroundable(...))` to show a progress bar in the status bar during indexing
- **Cannot run processes inside ReadAction**: completion handlers run under ReadAction, so process execution (artisan) must happen on a background thread. The service pre-caches results; the completion handler only reads the cached list
- **PHP discovery**: checks Herd path (`~/Library/Application Support/Herd/bin/php`), Homebrew (`/opt/homebrew/bin/php`), and system PATH
- **Auto-index**: optional file watcher via `VirtualFileManager.VFS_CHANGES` with 2-second debounce
- **Storage conversion**: the Data Source settings page has contextual buttons — "Convert to Database" (runs `php please install:eloquent-driver`) when on flat-file, "Export to Flat File" (runs all `php please eloquent:export-*` commands) when on Eloquent. Commands run in a `Task.Backgroundable` with progress bar

### Completion Auto-popup

`AntlersCompletionContributor.invokeAutoPopup()` returns `true` for `:` so that typing `{{ partial:` or `{{ collection:` immediately shows the completion popup without requiring Ctrl+Space.

## IDE Integration Patterns

### Settings

Feature toggles live in `AntlersSettings`, an application-level `PersistentStateComponent`, and are exposed at Settings > Languages & Frameworks > Statamic via `AntlersSettingsConfigurable`.

Every major feature should have a toggle. When adding a new toggleable feature:

1. Add `var enableXxx: Boolean = true` to `AntlersSettings.State`
2. Add a `JBCheckBox` in `AntlersSettingsConfigurable` under the appropriate `TitledSeparator`
3. Wire it through `isModified`, `apply`, and `reset`
4. Guard the feature entry point with `if (!AntlersSettings.getInstance().state.enableXxx) return`

#### Nested Settings Sub-pages

Settings are organized as nested sub-pages under Languages & Frameworks > Statamic:

- **Data Source** — driver detection, indexed resource counts, refresh button
- **Editor** — auto-close, semantic highlighting
- **Completion** — tags, modifiers, variables, parameters
- **Navigation & Documentation** — partial nav, custom tag nav, hover docs
- **Language Injection** — PHP, Alpine.js

Each sub-page is a separate `Configurable` class registered in `plugin.xml` with `parentId="com.statamic.toolkit.settings"`. The parent `AntlersSettingsConfigurable` implements `SearchableConfigurable` and renders a landing page with clickable links to each child.

To navigate within the already-open settings dialog (not open a new one), walk up the Swing hierarchy to find `com.intellij.openapi.options.newEditor.SettingsEditor`, look up the target configurable via `ConfigurableVisitor.findById(id, listOf(group))` using `ConfigurableExtensionPointUtil.getConfigurableGroup()`, and call `editor.select(configurable)`. Do **not** use `ShowSettingsUtil.showSettingsDialog()` from within a settings page — it opens a second dialog.

**Important**: `Configurable.Composite` alone does NOT create tree children in IntelliJ. Each child must be explicitly registered as an `applicationConfigurable` in `plugin.xml` with the correct `parentId`.

#### Settings configurable binding pattern

Each sub-page configurable uses a `CheckboxField(box, read, write)` data class to bind each `JBCheckBox` to its getter and setter in `AntlersSettings.State`.

The `fields: List<CheckboxField>` is built once. `isModified`, `apply`, and `reset` each collapse to a single `any` or `forEach` over that list. When adding a new toggle, add one entry to `fields` instead of updating three separate methods.

### Statamic Menu

The plugin exposes a top-level **Statamic** menu in the main menu bar for PHP-side Statamic workflows.

Rules:

- Content query snippets insert code at the caret and are enabled only when the active editor is a PHP file.
- The `Content Queries` submenu should remain visible in Laravel/Statamic projects even when the active tab is Antlers, so the menu does not look broken.
- Controller, tag, and modifier generators create files under `app/Http/Controllers`, `app/Tags`, and `app/Modifiers`.
- Generators should open an existing file instead of overwriting it if the class already exists.

Project-aware menu visibility is handled by lightweight action groups such as `StatamicProjectActionGroup` and `StatamicPhpInsertActionGroup`, not by duplicating enable/disable logic in every child action.

### Go-to Custom Tag/Modifier Definition

`AntlersGotoDeclarationHandler` chains two resolution strategies in order:

1. **Custom tag navigation** — unknown tag name → `app/Tags/ClassName.php` (guarded by `enableCustomTagNavigation`)
2. **Custom modifier navigation** — unknown modifier → `app/Modifiers/ClassName.php`

Partial navigation (`partial:name` → view file) is handled by the Antlers LSP server, not the goto handler.

Built-in Statamic tags (via `StatamicCatalog.isKnownTag()`) and block tags (via `AntlersBlockTags`) are skipped. Class name normalization uses `StatamicSnippetTemplates.normalizeTagClassName()` (same as the Statamic menu generators). File lookup uses `FilenameIndex` with a directory-scoped `GlobalSearchScope` limited to `app/Tags/` or `app/Modifiers/` for performance.

Known limitation: Statamic tag classes can set `protected static $handle = 'custom_name'` to decouple the class name from the tag name. The filename-based lookup will miss these.

### Extract Partial Intention

`ExtractPartialIntention` is an `IntentionAction` (not `PsiElementBaseIntentionAction`) that:

- Is available when the file is `.antlers.html`/`.antlers.php` AND the editor has a selection
- Prompts for a partial name via `Messages.showInputDialog()`
- Creates `resources/views/partials/{name}.antlers.html` using `VfsUtil.createDirectoryIfMissing()` + `VfsUtil.saveText()` (same pattern as `CreateStatamicTagAction`)
- Replaces the selection with `{{ partial:{name} }}`
- Opens the new file in the editor

Registered in plugin.xml with `<intentionAction>` and description resources in `src/main/resources/intentionDescriptions/ExtractPartialIntention/`.

### Status Bar Widget

`StatamicStatusBarWidgetFactory` creates a status bar icon that shows a custom popup on click (not an action menu — uses `JBPopupFactory.createComponentPopupBuilder()` with a `GridBagLayout` panel).

The popup displays driver type, indexing status, resource counts with handles, an auto-index checkbox, and a refresh button. It uses `RelativePoint` to position above the status bar.

### Structure View Nesting

`AntlersStructureViewElement` builds a virtual nested tree from the flat Antlers PSI by matching opening/closing tag pairs with a stack (same strategy as the folding builder). HTML landmark elements (`<header>`, `<main>`, `<nav>`, `<section>`, `<footer>`, `<article>`, `<aside>`) from the template data PSI tree are merged by document offset.

The `withChildren()` factory method creates elements with pre-computed children, enabling the opener node to display its block contents as a nested subtree without the PSI itself being nested.

### Navigation Bar Integration

`AntlersStructureAwareNavbar` extends `StructureAwareNavBarModelExtension`.

Rules:

- The abstract member is a Kotlin property, not a Java method. Use `override val language: Language = AntlersLanguage.INSTANCE`, not `override fun getLanguage()`.
- `getIcon()` does not exist on `AbstractNavBarModelExtension`; do not add it.
- The Antlers PSI tree is flat, so the navbar can show only within-`{{ }}` context. It cannot show tag-parent hierarchy for template content between tags.

### Find Usages and Symbols

`AntlersFindUsagesProvider` uses `DefaultWordsScanner` from `com.intellij.lang.cacheBuilder`, not `com.intellij.psi.search`.

Related type locations:

- `ChooseByNameContributorEx` is in `com.intellij.navigation`
- `IdFilter` is in `com.intellij.util.indexing`
- `FindSymbolParameters` is in `com.intellij.util.indexing`

## Performance and Stability

The plugin has several editor hot paths where small mistakes can freeze PhpStorm.

Rules:

- Partial navigation and completion must use `AntlersPartialPaths.searchScope(project)` rooted at `resources/views`, not `GlobalSearchScope.allScope(project)`.
- Do not add fallback `FilenameIndex` scans that search the whole project by filename only from goto handlers.
- JFlex states with custom start conditions need explicit `<<EOF>>` handling. Truncated Antlers blocks should reset to `YYINITIAL` and return `BAD_CHARACTER`.
- `AntlersFileViewProvider.supportsIncrementalReparse()` is intentionally `false`. Do not flip it without a reproducible case and validation against mixed Antlers/HTML PSI correctness.
- **Never run external processes inside a ReadAction.** Completion handlers run under ReadAction; calling `ScriptRunnerUtil.getProcessOutput()` from completion throws `Synchronous execution under ReadAction`. Use a background service (`executeOnPooledThread` or `Task.Backgroundable`) to pre-cache results, and have the completion handler read the cached data.
- **Post-format processor must use the Antlers PSI tree explicitly.** Call `file.viewProvider.getPsi(AntlersLanguage.INSTANCE)` — the `file` parameter may be the HTML PSI file, causing `file.children` to yield zero `AntlersAntlersTag` nodes.
- If the IDE freezes again, collect a thread dump or CPU snapshot before making more speculative performance changes.

## Build Environment

- **JDK 21 required** — set in `gradle.properties` as `javaVersion = 21`. The Gradle toolchain (`jvmToolchain(21)`) enforces this.
- IntelliJ IDEA CE's bundled JBR (Java 25) is too new for the Gradle build. Install JDK 21 separately (e.g. via IntelliJ's File > Project Structure > SDKs > Download JDK).
- Set `JAVA_HOME` when building from terminal: `export JAVA_HOME="/Users/$USER/Library/Java/JavaVirtualMachines/jbr-21.x.x/Contents/Home"`
- **Development target is PhpStorm 2025.1** (`platformVersion = 2025.1` in `gradle.properties`). The `sinceBuildVersion = 242` keeps compatibility back to 2024.2, but the sandbox IDE and LSP features target 2025.1. Grammar-Kit does not yet work with PhpStorm 2026.1.
- **PHP for Eloquent indexing**: the plugin finds PHP at Herd (`~/Library/Application Support/Herd/bin/php`), Homebrew (`/opt/homebrew/bin/php`), or system PATH. Herd paths contain spaces — `GeneralCommandLine` handles quoting automatically.

## Grammar and Parser Patterns

### Adding a New Keyword

1. Add a lexer rule in `Antlers.flex` with lookahead: `"keyword" / [^a-zA-Z0-9_]`
2. Add a token field in `AntlersTokenTypes.kt`: `@JvmField val KEYWORD_X = AntlersTokenType("KEYWORD_X")`
3. Add the token to `AntlersTokenSets.KEYWORDS`
4. Add the bare token name to the `tokens` block in `Antlers.bnf` and reference it in grammar rules
5. Run `./gradlew build` to regenerate everything

### BNF Error Recovery

- `pin=1` means "commit after matching the first token" and prevents backtracking.
- `recoverWhile` skips tokens until a predicate matches. Do not apply it to rules that can fail cleanly without consuming tokens, or it will create false errors.
- `private` rules do not generate PSI nodes and are useful for dispatch/grouping.
- If a grammar rule accepts only `IDENTIFIER` but the lexer produces a keyword token such as `KEYWORD_IF` for the same text, the parser throws `IDENTIFIER expected, got 'if'`. The fix is a private `tagNameAtom` rule that lists `IDENTIFIER | KEYWORD_IF | KEYWORD_UNLESS | ...` and is used wherever the original `IDENTIFIER` was. This is preferable to making those keywords context-sensitive in the lexer.

## Release and Maintenance

### Release Notes / What's New

Plugin update notes come from `CHANGELOG.md`, not handwritten `<change-notes>` in `plugin.xml`.

- `build.gradle.kts` extracts all version sections from `CHANGELOG.md` and feeds them into `pluginConfiguration.changeNotes`
- The converter maps `## [x.y.z]` headings to `<h2>`, `- ` items to `<ul><li>`, and inline `` ` `` to `<code>`
- `build/tmp/patchPluginXml/plugin.xml` is the fastest place to verify the generated `<change-notes>` block before publishing

### Release Process

Every version bump must update all of the following:

1. `pluginVersion` in `gradle.properties`
2. A new `## [x.y.z]` section at the top of `CHANGELOG.md` with user-facing bullet points
3. `README.md` features list and roadmap if the release adds visible features
4. `CLAUDE.md` if the release adds new subsystems or new patterns worth preserving

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
