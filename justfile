docs-serve-en:
    mdbook serve docs/en -p 3000

docs-serve-ja:
    mdbook serve docs/ja -p 3001

docs-serve:
    just docs-serve-en & just docs-serve-ja & wait

docs-build:
    mdbook build docs/en & mdbook build docs/ja
    mkdir -p docs/en/book/images & mkdir -p docs/ja/book/images
    cp -r docs/images/* docs/en/book/images/
    cp -r docs/images/* docs/ja/book/images/
