# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew build          # Generate lexer/parser from grammars + compile + package
./gradlew runIde         # Launch sandbox IDE with plugin installed
./gradlew buildPlugin    # Package plugin for distribution
./gradlew test           # Run tests (JUnit 4)
```

Grammar-Kit code generation (lexer from `.flex`, parser from `.bnf`) runs automatically before compilation — no separate step needed.

## Architecture

This is a **JetBrains IDE plugin** for the [Antlers](https://statamic.dev/frontend/antlers) template language (Statamic CMS). It provides syntax highlighting, code completion, and editor features for `.antlers.html` and `.antlers.php` files, and the current development target is **PhpStorm** (`platformType=PS` in `gradle.properties`).

### Dual PSI Tree (Template Language Framework)

The core architectural decision: Antlers files contain both Antlers expressions (`{{ }}`) and HTML/CSS/JS. The plugin uses IntelliJ's **Template Language Framework** (`MultiplePsiFilesPerDocumentFileViewProvider`) to maintain two simultaneous PSI trees from a single document:

- **Antlers tree** (base language): tags, expressions, conditionals, modifiers
- **HTML tree** (template data language): full HTML/CSS/JS intelligence

`AntlersFileViewProvider` overrides `findElementAt()` and `findReferenceAt()` to prefer the HTML tree for template data regions, falling back to the Antlers tree. This is what enables CSS class navigation, JS references, etc. in template files.

Important: the `TemplateDataElementType` outer fragment must use `OuterLanguageElementType`, not a plain PSI element type. Using the wrong outer element causes the IntelliJ Template Language Framework to throw `Wrong element created by ASTFactory` when switching files or reparsing mixed Antlers/HTML content.

### Code Generation Pipeline

Two Grammar-Kit grammars in `grammars/` generate code into `src/main/gen/` (gitignored):

1. **`Antlers.flex`** (JFlex) → `_AntlersLexer.java` — Stateful lexer with 7 states (YYINITIAL, ANTLERS_EXPR, COMMENT, PHP_RAW, PHP_ECHO, DQ_STRING, SQ_STRING, NOPARSE)
2. **`Antlers.bnf`** (Grammar-Kit BNF) → `AntlersParser.java` + PSI element types/interfaces/implementations

### Token Identity Bridge

The parser and lexer must share the same `IElementType` instances. `AntlersTokenTypes.factory()` uses reflection to resolve BNF token names to existing lexer token fields. The BNF `tokens` block lists bare names (no string values) so the factory receives field names like `"ANTLERS_OPEN"`, not display strings like `"{{"`.

### Highlighting Pipeline

`AntlersEditorHighlighter` extends `LayeredLexerEditorHighlighter`:
- Base layer: `AntlersSyntaxHighlighter` (Antlers token colors)
- Registered layer for `TEMPLATE_TEXT`: HTML/CSS/JS syntax highlighter

Antlers-specific semantic colors (tag names, parameter names, etc.) are added separately by `AntlersHighlightingAnnotator`, not by the lexer highlighter alone.

### Alpine.js Integration

Alpine support is implemented with a `MultiHostInjector` (`AntlersAlpineAttributeInjector`) plus a JavaScript `PsiReferenceContributor` (`AntlersAlpineReferenceContributor` / `AntlersAlpineReferenceResolver`).

- `x-data` is injected as a JS expression and wrapped with `(` `)` so object literals parse correctly.
- Event-like attributes (`@click`, `x-on:*`, `x-init`, `x-effect`) are injected as statements.
- `x-for` is **not** injected as JavaScript because Alpine's `(item, index) in items` syntax is not valid JS and causes parser noise.
- `x-for` loop aliases are instead resolved manually in the Alpine reference resolver so descendant expressions can still navigate and avoid false unresolved warnings.
- Cmd-click on Alpine method calls should resolve through normal PSI references first; `AntlersGotoDeclarationHandler` remains as a fallback path for Antlers-specific navigation like partials.

### PHP Injection

PHP intelligence inside `{{? ?}}` (raw PHP) and `{{$ $}}` (echo PHP) blocks is implemented via `AntlersPhpInjector`, a `MultiHostInjector` registered as an optional dependency on `com.jetbrains.php`.

- The BNF rules for `phpRawBlock` and `phpEchoBlock` use a `mixin` (`AntlersPhpBlockMixin`) that implements `PsiLanguageInjectionHost`, which is required for `MultiHostInjector.addPlace()`.
- `{{? ... ?}}` content is injected with prefix `<?php ` / suffix ` ?>` (statements).
- `{{$ ... $}}` content is injected with prefix `<?php echo ` / suffix `; ?>` (expression).
- The injector is registered in `antlers-php.xml` (loaded only when the PHP plugin is present) via `<depends optional="true" config-file="antlers-php.xml">com.jetbrains.php</depends>`.
- Formatting inside PHP blocks inherits from the user's PHP code style settings.

### Formatting

Formatting uses `TemplateLanguageFormattingModelBuilder`, not a plain formatting model builder. `AntlersFormattingModelBuilder` must special-case `OuterLanguageElementType` nodes and delegate those back to `SimpleTemplateLanguageFormattingModelBuilder`; otherwise mixed-template formatting breaks around template data boundaries.

### Statamic Catalog and Documentation

Official Statamic tags, modifiers, and variables are not maintained by hand anymore.

- Source of truth: `scripts/generate_statamic_catalog.py`
- Generated output: `src/main/kotlin/com/antlers/support/statamic/StatamicCatalogGenerated.kt`
- Runtime lookup layer: `StatamicCatalog`

This generated catalog powers:

- Antlers completion (`StatamicData`, `AntlersCompletionContributor`)
- Hover / quick documentation (`AntlersDocumentationProvider`)
- Official descriptions/examples/URLs for tags, modifiers, and variables

Variable vs. tag hover resolution is intentionally conservative: simple bare identifiers with no parameters can resolve as variables, while namespaced forms like `nav:foo` or `current_user:email` fall back to root tag/variable handles as needed.

### Tools Menu Actions

The plugin now exposes a `Tools > Statamic` menu for PHP-side Statamic workflows.

- Content query snippets insert code at the caret and are only enabled when the active editor is a PHP file.
- The `Content Queries` submenu itself should still stay visible in Laravel/Statamic projects even when the current tab is Antlers, so the menu does not look broken.
- Controller, tag, and modifier generators create files under `app/Http/Controllers`, `app/Tags`, and `app/Modifiers`.
- Generators should open an existing file instead of overwriting it if the class already exists.

Project-aware menu visibility is handled by lightweight action groups (`StatamicProjectActionGroup`, `StatamicPhpInsertActionGroup`) rather than duplicating enable/disable logic in every child action.

### Settings

Feature toggles live in `AntlersSettings` (application-level `PersistentStateComponent`) and are exposed in the settings panel at Settings > Languages & Frameworks > Antlers via `AntlersSettingsConfigurable`.

Every major feature should have a toggle. When adding a new toggleable feature:
1. Add a `var enableXxx: Boolean = true` field to `AntlersSettings.State`
2. Add a `JBCheckBox` in `AntlersSettingsConfigurable` under the appropriate `TitledSeparator` section
3. Wire it through `isModified`, `apply`, and `reset`
4. Guard the feature with `if (!AntlersSettings.getInstance().state.enableXxx) return` at the top of the entry point

Current sections: Editor, Completion, Navigation & Documentation, Language Injection.

### Release Notes / What's New

Plugin update notes are driven by `CHANGELOG.md`, not hand-written in `plugin.xml`.

- `build.gradle.kts` extracts **all** version sections from `CHANGELOG.md` and feeds them into `pluginConfiguration.changeNotes`. The converter handles `## [x.y.z]` headings → `<h2>`, `- ` items → `<ul><li>`, and inline `` ` `` → `<code>`.
- Verifying `build/tmp/patchPluginXml/plugin.xml` is the fastest way to confirm the generated `<change-notes>` block before publishing.

### Release Process

Every version bump must update all of the following:
1. `pluginVersion` in `gradle.properties`
2. New `## [x.y.z]` section at the top of `CHANGELOG.md` with user-facing bullet points
3. `README.md` features list and roadmap if the release adds visible features
4. `CLAUDE.md` architecture sections if the release adds new subsystems or patterns

## Key Patterns

### Adding a new keyword

1. Add lexer rule in `Antlers.flex` with lookahead: `"keyword" / [^a-zA-Z0-9_]`
2. Add token field in `AntlersTokenTypes.kt`: `@JvmField val KEYWORD_X = AntlersTokenType("KEYWORD_X")`
3. Add to `AntlersTokenSets.KEYWORDS`
4. Add bare token name to `Antlers.bnf` tokens block and reference in grammar rules
5. `./gradlew build` regenerates everything

### BNF error recovery

- `pin=1` on a rule means "commit after matching first token" — prevents backtracking
- `recoverWhile` skips tokens until predicate matches — but applying it to rules that can fail cleanly (no tokens consumed) causes false errors. Only use on rules that consume tokens before failing.
- `private` rules don't generate PSI nodes — use for dispatch/grouping

### Plugin dependencies

Runtime dependencies must be declared in both `build.gradle.kts` (`bundledPlugin()`) AND `plugin.xml` (`<depends>`). Missing either causes features to silently not work. Optional dependencies use `<depends optional="true" config-file="...">`.

## File Conventions

- Hand-written code: `src/main/kotlin/`
- Generated code: `src/main/gen/` (gitignored, regenerated on build)
- Grammar sources: `grammars/Antlers.flex`, `grammars/Antlers.bnf`
- Plugin manifest: `src/main/resources/META-INF/plugin.xml`
- Color schemes: `src/main/resources/colorSchemes/`
