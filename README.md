# Statamic Toolkit

A PhpStorm plugin providing rich editor support for the [Antlers](https://statamic.dev/frontend/antlers) template language used by [Statamic CMS](https://statamic.com).

## Features

- **Syntax Highlighting** for `.antlers.html` and `.antlers.php` files
  - Variables, tags, modifiers, operators, keywords
  - String and number literals
  - Comments (`{{# #}}`)
  - PHP regions (`{{? ?}}`, `{{$ $}}`)
  - Semantic colors for tag names, parameter names, and more
- **HTML, CSS, and JavaScript Intelligence** -- full template-aware editor support inside Antlers files via IntelliJ's Template Language framework
- **Official Statamic Completion** -- tags, modifiers, variables, common tag parameters, and scope-aware loop variables backed by the official Statamic docs
- **Hover Documentation** -- quick docs with examples and links to `statamic.dev` for tags, modifiers, and variables
- **PHP Intelligence** -- full PHP support inside `{{? ?}}` and `{{$ $}}` blocks with highlighting, completion, and formatting from your PhpStorm settings
- **Alpine.js Support** -- JavaScript intelligence inside Alpine attributes (`x-data`, `@click`, `x-bind`, `x-transition`, etc.) with method navigation back to `x-data`
- **Antlers Language Server** -- integrated Stillat LSP for formatting and diagnostics, bundled with the plugin
- **Partial Navigation** -- Cmd-click on `partial:name` to jump to the partial file
- **Tag Parameter Completion** -- suggests official parameters (`from=`, `limit=`, `sort=`, etc.) for common Statamic tags
- **Scope-Aware Variables** -- suggests `title`, `slug`, `url`, `first`, `last`, `count`, etc. inside collection/nav/taxonomy loops
- **Collection Handle Completion** -- `{{ collection: }}` suggests handles from flat-file or Eloquent driver (queries database via artisan)
- **Go-To Custom Tags/Modifiers** -- Cmd-click navigates to PHP classes in `app/Tags/` and `app/Modifiers/`
- **Extract to Partial** -- select code, Alt+Enter to extract into a new partial file
- **Structure View** -- Antlers tags + HTML landmarks (`<header>`, `<main>`, `<section>`, etc.) in document order with nested tag pair children
- **Formatting** -- template-aware formatting with mixed Antlers/HTML indentation, control-flow alignment, and block tag indentation for `collection`, `nav`, `cache`, `foreach`, `entries`, `groups`, `items`, and more
- **Auto-Close Tags** -- typing `{{ /` auto-completes the closing tag name; Enter after block tags auto-indents
- **Brace Matching** -- highlights matching `{{ }}` pairs
- **Block Commenting** -- toggle comments with `{{# #}}` via Ctrl+/ (Cmd+/)
- **Typing Aids** -- auto-closing braces, smart quotes, and smart enter handling
- **Status Bar Widget** -- shows Statamic indexing status, driver detection, resource counts, Antlers LSP connection state, quick links, refresh, and auto-index toggle
- **Statamic Menu** -- top-level menu with content query snippets plus controller, tag, and modifier generators
- **Customizable Colors** -- Settings > Editor > Color Scheme > Antlers, including underlined partial paths

## Supported Syntax

| Construct | Example |
|-----------|---------|
| Variables | `{{ title }}`, `{{ post:title }}` |
| Tags | `{{ collection:blog limit="5" }}...{{ /collection:blog }}` |
| Modifiers | `{{ title \| upper \| truncate(50) }}` |
| Conditionals | `{{ if }}`, `{{ elseif }}`, `{{ else }}`, `{{ unless }}` |
| Operators | `==`, `!=`, `&&`, `\|\|`, `??`, `?:`, `+`, `-`, `*`, `/` |
| Assignment | `{{ total = price * quantity }}` |
| Comments | `{{# This is a comment #}}` |
| PHP | `{{? $var = doSomething(); ?}}`, `{{$ route('home') $}}` |
| Self-closing | `{{ partial:hero /}}` |
| Escaped | `@{{ not_parsed }}` |

## Installation

### From JetBrains Marketplace
*Coming soon*

### From Source

```bash
git clone https://github.com/portseif/statamic-toolkit-phpstorm.git
cd statamic-toolkit-phpstorm
./gradlew runIde
```

This launches a sandboxed IDE instance with the plugin installed.

## Requirements

- PhpStorm 2024.2+
- JDK 21
- Node.js available on your machine for the bundled Antlers Language Server

## Building

```bash
./gradlew build          # Build the plugin
./gradlew test           # Run tests
./gradlew runIde         # Launch sandbox IDE
./gradlew buildPlugin    # Package as .zip for distribution
```

## Roadmap

- [x] Syntax highlighting
- [x] Brace matching
- [x] Block commenting
- [x] HTML/CSS/JS intelligence in template files
- [x] Code completion for Statamic tags and modifiers
- [x] Go-to-definition for partials
- [x] Formatting support
- [x] Structure view
- [x] Alpine.js support
- [x] PHP intelligence in PHP blocks
- [x] Hover documentation
- [x] Statamic generators (controllers, tags, modifiers)
- [x] Configurable settings panel
- [x] Code folding for tag pairs
- [x] Tag parameter completion
- [x] Scope-aware variable completion
- [x] Go-to custom tag/modifier definition
- [x] Extract to partial refactoring
- [x] Antlers Language Server integration
- [x] Eloquent driver support
- [x] Status bar indexing widget
- [ ] Live templates / snippets
- [ ] Blueprint field completion

## Disclaimer

This plugin is not affiliated with, endorsed by, or officially connected to Statamic. "Statamic" and the Statamic logo are trademarks of [Statamic](https://statamic.com).

## License

MIT
