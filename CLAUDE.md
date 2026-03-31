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

**Statamic Toolkit** is a JetBrains IDE plugin for the [Antlers](https://statamic.dev/frontend/antlers) template language (Statamic CMS). It provides syntax highlighting, code completion, and editor features for `.antlers.html` and `.antlers.php` files, and the current development target is **PhpStorm** (`platformType=PS` in `gradle.properties`).

The plugin name is **Statamic Toolkit** (marketplace display name), but the language is **Antlers**. All language-level registrations (file type, parser, highlighter, color scheme, code style) use "Antlers". The settings panel under Languages & Frameworks uses **Statamic**.

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
- `AntlersAlpineReferenceContributor` must stay cheap: do not eagerly call the full resolver from `getReferencesByElement()`. Reference creation happens often enough that double-resolution can become an editor freeze amplifier.
- `AntlersAlpineReferenceResolver` caches the injected `x-data` object literal lookup per `XmlAttributeValue`. Re-materializing injected PSI while walking ancestors is expensive and should be avoided.

### PHP Injection

PHP intelligence inside `{{? ?}}` (raw PHP) and `{{$ $}}` (echo PHP) blocks is implemented via `AntlersPhpInjector`, a `MultiHostInjector` registered as an optional dependency on `com.jetbrains.php`.

- The BNF rules for `phpRawBlock` and `phpEchoBlock` use a `mixin` (`AntlersPhpBlockMixin`) that implements `PsiLanguageInjectionHost`, which is required for `MultiHostInjector.addPlace()`.
- `{{? ... ?}}` content is injected with prefix `<?php ` / suffix ` ?>` (statements).
- `{{$ ... $}}` content is injected with prefix `<?php echo ` / suffix `; ?>` (expression).
- The injector is registered in `antlers-php.xml` (loaded only when the PHP plugin is present) via `<depends optional="true" config-file="antlers-php.xml">com.jetbrains.php</depends>`.
- Formatting inside PHP blocks inherits from the user's PHP code style settings.

### Formatting

Formatting uses `TemplateLanguageFormattingModelBuilder`, not a plain formatting model builder. `AntlersFormattingModelBuilder` must special-case `OuterLanguageElementType` nodes and delegate those back to `SimpleTemplateLanguageFormattingModelBuilder`; otherwise mixed-template formatting breaks around template data boundaries.

`AntlersBlock` (a `TemplateLanguageBlock`) holds a `SpacingBuilder` that enforces token-level spacing rules: one space inside `{{ }}` delimiters, one space around operators, no space around `=` in parameters or `:` in modifier args, no space around `/`. The `SpacingBuilder` is instantiated once per block (in the constructor) and its `getSpacing()` is called before the super-class fallback.

**Known limitation — `{{ else }}` / `{{ elseif }}` alignment**: IntelliJ's template language formatter places these blocks as children of the `DataLanguageBlockWrapper` spanning the HTML content between `{{ if }}` and themselves. That wrapper carries the surrounding HTML indent (e.g. col 8 inside `<main>`). `getIndent()` is relative to the parent block, so there is no way to say "align with sibling `{{ if }}`" using the standard `Indent` API — `getNoneIndent()` preserves the parent indent, and `getAbsoluteNoneIndent()` forces col 0. This is a structural limitation of the template language formatter framework that Blade and Twig plugins share. Fixing it would require a custom formatting model that separates Antlers-level indent decisions from the HTML tree entirely.

**`OP_DIVIDE` spacing**: Use `.around(OP_DIVIDE).none()` in the `SpacingBuilder`, not `.spaces(1)` and not omission. In Antlers, `/` serves as a path separator (`partial:partials/sections/hero`) far more often than an arithmetic operator. `.none()` actively removes previously-introduced spaces on Reformat Code; omitting the rule returns `null` (no opinion, keep existing whitespace) and does nothing to compress them.

**Closing-tag grammar fix**: `{{ /if }}` previously caused a parse error because `tagName` only accepted `IDENTIFIER`, but `if` is lexed as `KEYWORD_IF`. The fix is a `private tagNameAtom` rule in `Antlers.bnf` that accepts `IDENTIFIER | KEYWORD_IF | KEYWORD_UNLESS | KEYWORD_SWITCH`. After this change, `{{ /if }}` parses as a `closingTag` node rather than a `conditionalTag`, so `AntlersFoldingBuilder`'s closing-tag branch must map `"if" → "COND_IF"` and `"unless" → "COND_UNLESS"` to match the stack keys used by the opening conditional branch.

### Code Folding

`AntlersFoldingBuilder` (extends `FoldingBuilderEx`, implements `DumbAware`) uses a stack of `OpenFold(tag, key)` entries to match opening and closing tags in document order. All Antlers tags are siblings under the root `antlersFile` node (flat PSI — no hierarchy), so a traversal of `root.children` with a stack is the only viable matching strategy.

- Regular tag pairs push `tagExpr.tagName.text` as the key; closing tags pop by that name.
- Conditional pairs use synthetic keys `"COND_IF"` / `"COND_UNLESS"` to avoid colliding with any tag named `"if"`.
- `{{ /if }}` and `{{ /unless }}` parse as `closingTag` after the grammar fix, so the closing branch remaps `"if" → "COND_IF"` and `"unless" → "COND_UNLESS"` before the stack lookup.
- `getPlaceholderText` shows the full expression/condition text (trimmed, truncated at 60 chars) so folded blocks read as `{{ if site:environment === 'production' }}...` rather than `{{ if }}...`.

### Settings Configurable Pattern

`AntlersSettingsConfigurable` uses a `CheckboxField(box, read, write)` data class to bind each `JBCheckBox` to its getter/setter in `AntlersSettings.State`. The `fields: List<CheckboxField>` is built once; `isModified`, `apply`, and `reset` each collapse to a single `any`/`forEach` call over the list. When adding a new toggle, add one entry to the `fields` list rather than touching three separate methods.

### Completion Pre-building

`StatamicData` pre-builds `TAG_ELEMENTS`, `MODIFIER_ELEMENTS`, `VARIABLE_ELEMENTS` (`lazy List<LookupElement>`) and `SUB_TAG_ELEMENT_MAP` (`lazy Map<String, List<LookupElement>>`) at class-load time. `AntlersCompletionContributor` calls `result.addAllElements(StatamicData.XXX_ELEMENTS)` — never allocates `LookupElement` instances per keystroke. Do not revert to per-invocation construction.

### Performance / Stability Guardrails

The plugin has a few editor hot paths where small mistakes are enough to freeze PhpStorm:

- Partial navigation and completion must use a narrow search scope (`AntlersPartialPaths.searchScope(project)`) rooted at `resources/views`, not `GlobalSearchScope.allScope(project)`.
- Do not add fallback `FilenameIndex` scans that search the whole project by filename only from goto handlers. Those can run on UI-triggered paths and are a realistic freeze source in large projects.
- JFlex states with custom start conditions need explicit `<<EOF>>` handling. Truncated Antlers blocks should reset to `YYINITIAL` and return `BAD_CHARACTER` instead of leaving the lexer in a bad state.
- `AntlersFileViewProvider.supportsIncrementalReparse()` is still `false` on purpose. Do not flip it to `true` without a reproducible case and validation against mixed Antlers/HTML PSI correctness.
- If the IDE freezes again, prefer collecting a thread dump or CPU snapshot before making more speculative performance changes.

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

### Statamic Menu

The plugin exposes a top-level **Statamic** menu in the main menu bar for PHP-side Statamic workflows.

- Content query snippets insert code at the caret and are only enabled when the active editor is a PHP file.
- The `Content Queries` submenu itself should still stay visible in Laravel/Statamic projects even when the current tab is Antlers, so the menu does not look broken.
- Controller, tag, and modifier generators create files under `app/Http/Controllers`, `app/Tags`, and `app/Modifiers`.
- Generators should open an existing file instead of overwriting it if the class already exists.

Project-aware menu visibility is handled by lightweight action groups (`StatamicProjectActionGroup`, `StatamicPhpInsertActionGroup`) rather than duplicating enable/disable logic in every child action.

### Settings

Feature toggles live in `AntlersSettings` (application-level `PersistentStateComponent`) and are exposed in the settings panel at Settings > Languages & Frameworks > Statamic via `AntlersSettingsConfigurable`.

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
- When a grammar rule only accepts `IDENTIFIER` but the lexer produces a keyword token (e.g. `KEYWORD_IF`) for the same text, the parser throws "IDENTIFIER expected, got 'if'". The fix is a `private tagNameAtom` rule that lists `IDENTIFIER | KEYWORD_IF | KEYWORD_UNLESS | ...` and is used wherever the original `IDENTIFIER` was. This is preferable to making those keywords context-sensitive in the lexer.

### `StructureAwareNavBarModelExtension`

`AntlersStructureAwareNavbar` extends `StructureAwareNavBarModelExtension`. The abstract member is a **Kotlin property**, not a Java method — use `override val language: Language = AntlersLanguage.INSTANCE`, not `override fun getLanguage()`. `getIcon()` does not exist on `AbstractNavBarModelExtension`; do not add it. The Antlers PSI tree is flat (all tags are siblings under `antlersFile`), so the navbar can only show within-`{{ }}` context, not tag-parent hierarchy for template content between tags.

### Find Usages

`AntlersFindUsagesProvider` uses `DefaultWordsScanner` from `com.intellij.lang.cacheBuilder` (not `com.intellij.psi.search`). `ChooseByNameContributorEx` is in `com.intellij.navigation` (not `com.intellij.ide.util.gotoByName`). `IdFilter` and `FindSymbolParameters` are in `com.intellij.util.indexing`.

### Plugin dependencies

Runtime dependencies must be declared in both `build.gradle.kts` (`bundledPlugin()`) AND `plugin.xml` (`<depends>`). Missing either causes features to silently not work. Optional dependencies use `<depends optional="true" config-file="...">`.

## File Conventions

- Hand-written code: `src/main/kotlin/`
- Generated code: `src/main/gen/` (gitignored, regenerated on build)
- Grammar sources: `grammars/Antlers.flex`, `grammars/Antlers.bnf`
- Plugin manifest: `src/main/resources/META-INF/plugin.xml`
- Color schemes: `src/main/resources/colorSchemes/`
