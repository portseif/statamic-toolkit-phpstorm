# Changelog

## [0.8.0]
- **Antlers Language Server** — integrated the Stillat Antlers LSP for formatting and diagnostics, bundled with the plugin (requires Node.js)
- **LSP visibility in the status widget** — the Statamic popup now surfaces Antlers LSP connection state alongside indexing status so it is obvious when the language server is waiting, starting, connected, or failed
- **Tag parameter completion** — typing inside `{{ collection }}` now suggests `from=`, `limit=`, `sort=`, `paginate=`, and all official parameters for 14 common tags
- **Scope-aware variable completion** — inside `{{ collection }}` loops, suggests `title`, `slug`, `url`, `date`, `first`, `last`, `count`, `total_results`, and other contextual fields. Also works inside `nav`, `taxonomy`, `search`, `assets`, and `form` blocks
- **Collection handle completion** — `{{ collection: }}` auto-suggests collection handles from your project, supporting both flat-file and Eloquent driver (queries the database via artisan)
- **Go-to custom tag/modifier definition** — Cmd-click on unknown tag names navigates to `app/Tags/ClassName.php`, modifiers to `app/Modifiers/ClassName.php`
- **Extract to Partial refactoring** — select code, Alt+Enter, "Extract to Antlers partial" creates a new partial file and replaces the selection with `{{ partial:name }}`
- **Official Statamic docs in-editor** — hover documentation and completion for tags, modifiers, and variables are backed by the official Statamic docs catalog with examples and deep links
- **Enhanced syntax highlighting** — all parts of tag names are now colored (not just the head), and partial paths (`partial:components/hero`) are underlined to signal navigability
- **Structure View with HTML landmarks** — `<header>`, `<main>`, `<nav>`, `<section>`, `<footer>`, `<article>`, `<aside>` now appear in the Structure panel alongside Antlers tags, in document order. Tag pair blocks nest their contents as children
- **Improved formatting** — Reformat Code now handles mixed Antlers/HTML structure more reliably, including `{{ if }}` / `{{ else }}` alignment, HTML parent nesting, and flat runs of standalone partial tags
- **Auto-indent on Enter** — pressing Enter after a block tag opener auto-indents the new line
- **Auto-close tag on `/`** — typing `{{ /` inside a block automatically completes the closing tag name
- **Statamic status bar widget** — shows indexing progress with driver type, indexed resource counts, clickable resource directories, a quick link to `resources/views`, and an auto-index toggle
- **Nested settings pages** — Settings > Languages & Frameworks > Statamic is now organized into sub-pages: Data Source, Editor, Completion, Navigation & Documentation, Language Injection
- **Eloquent driver auto-detection** — reads `config/statamic/eloquent-driver.php` to detect database-backed collections and queries all Statamic facades (collections, navigations, taxonomies, globals, forms, asset containers)
- **Auto-close brace fix** — fixed `{{ }}` auto-closer producing triple `}}}` due to IntelliJ's built-in single-brace pairing
- **Inspections disabled by default** — unknown tag/modifier inspections are now off by default since the LSP provides better diagnostics
- **Alpine.js false-positive cleanup** — reduced noise from mixed Antlers/Alpine markup, including `x-transition:*` namespace warnings and `x-for`-scoped variable false errors

## [0.7.4]
- Fixed formatter adding spaces around `/` — `partial:partials/sections/hero` now stays compact. The formatter actively removes any previously-introduced spaces on Reformat Code.

## [0.7.3]
- Fixed formatter adding spaces inside partial paths — `partial:partials/sections/hero` no longer becomes `partial:partials / sections / hero`. The `/` path separator is now left untouched by the spacing rules.
- Fixed `{{ else }}`, `{{ elseif }}`, `{{ endif }}`, and `{{ endunless }}` being indented as content inside `{{ if }}` after Reformat Code. They now realign to the same column as their opening `{{ if }}` / `{{ unless }}` tag.

## [0.7.2]
- **Reformat Code** now works for Antlers expressions — spacing is enforced around operators (`===`, `&&`, `||`, `+`, etc.), pipes (`|`), delimiters (`{{ }}`), and colons/commas in modifier arguments.
- **Folded block labels** now show the full expression instead of just the keyword — e.g. `{{ if site:environment === 'production' }}...` instead of `{{ if }}...`. Labels truncate at 60 characters.

## [0.7.1]
- Fixed parse error "IDENTIFIER expected, got 'if'" on `{{ /if }}` and `{{ /unless }}` closing tags. The grammar now accepts keyword-named closing forms alongside regular identifier names.
- Fixed formatter incorrectly indenting `{{ else }}`, `{{ elseif }}`, and `{{ endif }}` as if they were content inside the `{{ if }}` block instead of sibling tags at the same level.

## [0.7.0]
- **Go To Symbol** (Cmd+Alt+O) now includes all Antlers partial templates. Type a partial name to jump directly to the file without needing to know the full path.
- **Navigation bar breadcrumb** shows the Antlers tag context when the caret is inside a `{{ ... }}` expression — e.g., `_card.antlers.html  >  {{ collection:blog }}` or `>  {{ if }}`.

## [0.6.0]
- **Code folding** for Antlers tag pairs (`{{ collection:blog }}...{{ /collection:blog }}`), conditional blocks (`{{ if }}...{{ endif }}`, `{{ unless }}...{{ endunless }}`), multi-line comments (`{{# ... #}}`), and `{{ noparse }}` blocks. Nested pairs fold independently.
- **Find Usages** for Antlers tag names and modifiers — right-click any tag name or modifier and choose Find Usages to see every reference across your templates.
- **Unknown modifier inspection** — weak warning on modifiers not found in the Statamic catalog. Toggleable under Settings > Editor > Inspections > Antlers.
- **Unknown tag inspection** — weak warning on tag names not found in the Statamic catalog. Handles namespaced sub-tags (`nav:breadcrumbs`) correctly. Toggleable under Settings > Editor > Inspections > Antlers.

## [0.5.1]
- Alpine.js `x-transition` attributes (`:enter`, `:leave`, `:enter-start`, `:enter-end`, `:leave-start`, `:leave-end`) are now recognized — no more "namespace not bound" warnings.
- Completion popup is faster: `LookupElement` instances for tags, modifiers, variables, and sub-tags are built once at startup and reused on every invocation instead of being reallocated per keystroke.
- Internal performance fixes: token factory lookup is now O(1) via a lazy map instead of per-call reflection; syntax highlighter uses a pre-built token→color map instead of a linear `when`-chain; annotator skips non-identifier PSI elements before any parent or settings access.
- Language instances for JavaScript and PHP injection are cached at class load instead of being resolved on every injection call.

## [0.5.0]
- Renamed plugin to **Statamic Toolkit**.
- Top-level **Statamic** menu in the main menu bar (moved from Tools).
- Improved hover documentation cards with code-styled examples and clickable doc links.
- Separate color scheme entry for **Modifiers** (Editor > Color Scheme > Antlers).
- Performance and stability fixes for Alpine.js reference resolution and partial navigation.

## [0.4.0]
- PHP intelligence inside `{{? ?}}` and `{{$ $}}` blocks with syntax highlighting, completion, and formatting from your PhpStorm settings.
- New settings panel with organized sections: Editor, Completion, Navigation & Documentation, and Language Injection.
- Toggles for hover documentation, Alpine.js injection, PHP injection, and semantic highlighting.

## [0.3.0]
- Statamic tag, modifier, and variable completion with hover documentation from the official docs.
- Tools > Statamic menu with controller, tag, and modifier generators plus content query snippets.
- Full Antlers grammar with improved error recovery.

## [0.2.0]
- Alpine.js support with JavaScript intelligence in Alpine attributes and method navigation.
- Template-aware formatting for mixed Antlers/HTML files.
- Semantic syntax highlighting for tag names, parameters, and modifiers.
- Structure view, typing aids, and code style settings.

## [0.1.0]
- Syntax highlighting for `.antlers.html` and `.antlers.php` files.
- Full HTML/CSS/JS intelligence inside Antlers template files.
- Brace matching, block commenting, and partial navigation.
