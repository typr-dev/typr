
## Building

`typo` helps dog-food the experimental [bleep](https://bleep.build/docs/) Scala build tool while it is approaching first public release. Keep an open mind!

- `git clone https://github.com/oyvindberg/typr`
- [install bleep](https://bleep.build/docs/installing/)
- run `docker compose up` to get example database
- run `bleep install-tab-completions-zsh` / `bleep install-tab-completions-bash` and start a new shell to get tab completions
- `bleep setup-ide jvm213` to enable IDE import (metals or intellij)
-  open in your IDE
- `bleep test` to run tests
- run for instance `bleep generate-adventureworks` to regenerate adventureworks database
