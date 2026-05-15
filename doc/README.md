# re-do documentation

Documentation for the **re-do** project. Built with [MkDocs](https://www.mkdocs.org/) and the
[Material](https://squidfunk.github.io/mkdocs-material/) theme.

## Layout

```
doc/
├── mkdocs.yml      MkDocs configuration and navigation
└── docs/           Documentation source (Markdown)
```

## Build and preview locally

```bash
# Install once
pip install mkdocs-material

# Serve at http://127.0.0.1:8000
cd doc && mkdocs serve

# Produce a static site under doc/site/
cd doc && mkdocs build
```

## Editing

- All pages live under `doc/docs/`.
- Navigation order is defined in `doc/mkdocs.yml` under `nav:`.
- Keep pages short; cross-link rather than duplicating.
