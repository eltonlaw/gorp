# gorp

[![Clojars Project](https://img.shields.io/clojars/v/com.github.eltonlaw/gorp.svg?include_prereleases)](https://clojars.org/com.github.eltonlaw/gorp)

## Setup

Have this in your deps.edn
```
{:aliases
 {:gorp {:extra-deps {eltonlaw/gorp {:mvn/version "0.1.0"}}
         ;; if using clj-async-profiler
         :jvm-opts ["-Djdk.attach.allowAttachSelf"]
         :main-opts ["-m" "gorp.main"]}}}
```
and invoke the regular way to start the repl
```
clj -M:gorp
```

## Usage

For custom code, a `gorp_init.clj` will be loaded if it exists in any of current working dir, `$HOME` or `$XDF_CONFIG_HOME`.

## Development

### Clojars deploy

```
clj -T:build clean
clj -T:build jar
clj -T:build deploy
```
