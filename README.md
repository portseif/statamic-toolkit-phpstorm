# Antlers Support

A JetBrains IDE plugin providing syntax highlighting and editor support for the [Antlers](https://statamic.dev/frontend/antlers) template language used by [Statamic CMS](https://statamic.com).

## Features

- **Syntax Highlighting** for `.antlers.html` and `.antlers.php` files
  - Variables, tags, modifiers, operators, keywords
  - String and number literals
  - Comments (`{{# #}}`)
  - PHP regions (`{{? ?}}`, `{{$ $}}`)
- **HTML Intelligence Preserved** -- full HTML/CSS/JS support within template files via IntelliJ's Template Language framework
- **Brace Matching** -- highlights matching `{{ }}` pairs
- **Block Commenting** -- toggle comments with `{{# #}}` via Ctrl+/ (Cmd+/)
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
- [ ] Code folding for tag pairs
- [ ] Code completion for Statamic tags and modifiers
- [ ] Go-to-definition for partials
- [ ] Live templates / snippets
- [ ] Formatting support
- [ ] Structure view

## Disclaimer

This plugin is not affiliated with, endorsed by, or officially connected to Statamic. "Statamic" and the Statamic logo are trademarks of [Wilderborn](https://wilderborn.com).

## License

MIT
