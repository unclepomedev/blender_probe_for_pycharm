${decorators}    @property
    def ${name}(self) -> ${type_hint}:
${doc}
        ...
${decorators}    @${name}.setter
    def ${name}(self, value: ${type_hint}) -> None:
        ...