# boot-sources
[![Clojars Project](https://img.shields.io/clojars/v/powerlaces/boot-sources.svg)](https://clojars.org/powerlaces/boot-sources)

Boot tasks that collects source files `#{.clj .cljs .cljc .js}` and do
something with them.

```clojure
(set-env! :dependencies '[[powerlaces/boot-sources "X.Y.Z" :scope "test"]])
(require '[powerlaces.boot-sources :refer :all])
```

## Usage

#### pack-sources

Include `pack-sources` at any point of your task chain. For instance from the
command line:

```
boot cljs sass pack-sources -d "org.clojure/clojurescript:1.8.34" target
```

If you look in the `target` folder you will see that a `clj-src` dir has been
created (the default if you don't specify `:to-dir`) and it now contains the
ClojureScript sources.

As usual, `boot pack-sources -h` shows you the option summary.

Note that if you don't specify `-d|--dependencies`, the current `(get-env)`
will be queried and all the dependencies in `build.boot` will be included.

This time in the repl, another example that dumps everything to the `target`
folder:

```clojure
(require '[powerlaces.boot-sources :refer [pack-sources]])

(boot (pack-sources :dependencies #{['org.clojure/clojurescript "1.8.34"]}
                    :exclude #{#"project.clj"
                               #"third_party\/closure\/.*base.js$"
                               #"third_party\/closure\/.*deps.js$"
                               #"org\/clojure\/clojure\/.*$"}
                    :exclusions '#{org.clojure/clojure
                                   org.mozilla/rhino})
      (built-in/target))
```

This is particularly useful for
[self-hosted REPL](https://github.com/Lambda-X/replumb) apps, which requires
(pun intended) source files along with the deployed app in order to work
properly. For more info see
[here](http://lambdax.io/blog/posts/2015-12-21-cljs-replumb-require.html).
  
## Contributing

I suggest first of all to open an issue explaining what is missing and why you
think it should be added. If the reason is compelling, run `boot auto-test` and
freely hack away.

## License

Distributed under the Eclipse Public License, the same as Clojure.

Copyright (C) 2016 Andrea Richiardi & Scalac Sp. z o.o.
Copyright (C) 2017 Andrea Richiardi
