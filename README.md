# Antlers

A JetBrains IDE plugin providing syntax highlighting and editor support for the [Antlers](https://statamic.dev/frontend/antlers) template language used by [Statamic CMS](https://statamic.com).

## Features

- **Syntax Highlighting** for `.antlers.html` and `.antlers.php` files
  - Variables, tags, modifiers, operators, keywords
  - String and number literals
  - Comments (`{{# #}}`)
  - PHP regions (`{{? ?}}`, `{{$ $}}`)
  - Semantic colors for tag names, parameter names, and more
- **HTML Intelligence Preserved** -- full HTML/CSS/JS support within template files via IntelliJ's Template Language framework
- **Code Completion** -- Statamic tags, modifiers, and variables with descriptions from the official docs
- **Hover Documentation** -- quick docs with examples and links to statamic.dev
- **PHP Intelligence** -- full PHP support inside `{{? ?}}` and `{{$ $}}` blocks with highlighting, completion, and formatting from your PhpStorm settings
- **Alpine.js Support** -- JavaScript intelligence inside Alpine attributes (`x-data`, `@click`, `x-bind`, etc.) with method navigation back to `x-data`
- **Partial Navigation** -- Cmd-click on `partial:name` to jump to the partial file
- **Structure View** -- outline of Antlers tags in the Structure tool window
- **Formatting** -- template-aware formatting for mixed Antlers/HTML files
- **Brace Matching** -- highlights matching `{{ }}` pairs
- **Block Commenting** -- toggle comments with `{{# #}}` via Ctrl+/ (Cmd+/)
- **Typing Aids** -- auto-closing braces, smart quotes, and smart enter handling
- **Tools > Statamic** -- controller, tag, and modifier generators plus content query snippets in PhpStorm
- **Customizable Colors** -- Settings > Editor > Color Scheme > Antlers

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
git clone https://github.com/portseif/antlers-support.git
cd antlers-support
./gradlew runIde
```

This launches a sandboxed IDE instance with the plugin installed.

## Requirements

- IntelliJ IDEA / PhpStorm 2024.2+
- JDK 21

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
- [ ] Code folding for tag pairs
- [ ] Live templates / snippets

## Disclaimer

This plugin is not affiliated with, endorsed by, or officially connected to Statamic. "Statamic" and the Statamic logo are trademarks of [Statamic](https://statamic.com).

## License

MIT
