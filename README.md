# gorp

[![Clojars Project](https://img.shields.io/clojars/v/com.github.eltonlaw/gorp.svg?include_prereleases)](https://clojars.org/com.github.eltonlaw/gorp)

## setup

Have this in your deps.edn
```
{:aliases
 {:gorp {:extra-deps {eltonlaw/gorp {:mvn/version "0.1.0"}}
         :main-opts ["-m" "gorp.main"]}}}
```
and invoke the regular way to start the repl
```
clj -M:gorp
```

## dev

```
clj -T:build clean
clj -T:build jar
clj -T:build deploy
```
