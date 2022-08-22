# gorp

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
