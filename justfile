docs-build:
    mdbook build docs/en & mdbook build docs/ja
    mkdir -p docs/en/book/images docs/ja/book/images
    cp -r docs/images/* docs/en/book/images/
    cp -r docs/images/* docs/ja/book/images/

docs-open:
    open docs/en/book/index.html && open docs/ja/book/index.html
