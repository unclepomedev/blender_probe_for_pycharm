docs-build:
    mkdir -p docs/en/theme && cp -r docs/theme/* docs/en/theme/
    mkdir -p docs/ja/theme && cp -r docs/theme/* docs/ja/theme/
    mdbook build docs/en && mdbook build docs/ja
    mkdir -p docs/en/book/images docs/ja/book/images
    cp -r docs/images/* docs/en/book/images/
    cp -r docs/images/* docs/ja/book/images/

docs-open:
    open docs/en/book/index.html && open docs/ja/book/index.html
