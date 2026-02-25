docs-serve-en:
    mdbook serve docs/en -p 3000

docs-serve-ja:
    mdbook serve docs/ja -p 3001

docs-serve:
    just docs-serve-en & just docs-serve-ja & wait

docs-build:
    mdbook build docs/en & mdbook build docs/ja
