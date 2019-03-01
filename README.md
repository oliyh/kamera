# kamera

Visual testing tools for Clojure with figwheel integration.

When data is represented visually for a human to view great care must be taken to present it intuitively, accessibly
and beautifully. This requires skill and time, and above all requires human judgement to attest to its efficacy.

Once this has been achieved it would be nice to ensure that it does not suffer regressions. kamera is a library designed to
help you capture and compare screenshots of your application, failing if there is too much divergence between an expected
reference image and the current display.

The best way to test visual representation is to create [devcards](https://github.com/bhauman/devcards)
which you can use to display components in as many known states as possible. If you ensure you separate rendering from
business logic you can ensure that refactoring will not affect them and prevent them becoming brittle - I outlined this approach
in a [blog post for JUXT](https://juxt.pro/blog/posts/cljs-apps.html).

kamera has figwheel integration to allow it to scan all your devcards automatically - you just need to provide it with
the directory where reference versions reside.

## Usage

See the [example project](https://github.com/oliyh/kamera/tree/master/example) for a full working example.

If you have a figwheel-main build called "dev" which has devcards enabled and a page served from /devcards.html then you
can test your devcards as simply as this:

### Reference images

Given the following devcards test files:

```bash
test
└── example
    ├── another_core_test.cljs # has devcards
    ├── core_test.cljs         # has devcards
    ├── devcards.cljs          # runs the devcards ui
    └── test_runner.cljs       # runs unit tests
```

... and a directory populated with reference images like this:
(you can generate these images initially by running kamera and copying the "actual" files from the screenshot directory)

```
test-resources
└── kamera
    ├── example.another_core_test.png
    └── example.core_test.png
```

You can run the following code to compare all the images:

```clojure
(ns example.devcards-test
  (:require [kamera.figwheel :as kf]
            [clojure.test :refer [deftest testing is]]))

(deftest devcards-test
  (let [build-id "dev"
        opts (-> kf/default-opts
                 (update :default-target merge {:reference-directory "/test-resources/kamera"
                                                :screenshot-directory "/target/kamera"}))]

    (kf/test-devcards build-id opts)))
```

The output will look something like this:



## Development

Start a normal Clojure REPL

[![CircleCI](https://circleci.com/gh/oliyh/kamera.svg?style=svg)](https://circleci.com/gh/oliyh/kamera)

## License

Copyright © 2018 oliyh

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
