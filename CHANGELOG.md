# Changelog

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
