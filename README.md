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

### Devcards (actual)

The following devcards test files:

```bash
test
└── example
    ├── another_core_test.cljs # has devcards
    ├── core_test.cljs         # has devcards
    ├── devcards.cljs          # runs the devcards ui
    └── test_runner.cljs       # runs unit tests
```

will have screenshots taken to produce 'actual' images called `example.core_test.png`, `example.another_core_test.png`.

### References (expected)

A directory populated with reference images, named in the same way, provides the 'expected' screenshots:

```
test-resources
└── kamera
    ├── example.another_core_test.png
    └── example.core_test.png
```

![](example/test-resources/kamera/example.another_core_test.png?raw=true)
![](example/test-resources/kamera/example.core_test.png?raw=true)

_(You can generate these images initially by running kamera and copying the 'actual' files from the screenshot directory)_

### Run the test

The following code will start the figwheel build called `dev`, launch Chrome, take screenshots of all the devcards
and compare the actual with the expected, failing if the difference is above a certain threshold.

```clojure
(ns example.devcards-test
  (:require [kamera.figwheel :as kf]
            [clojure.test :refer [deftest testing is]]))

(deftest devcards-test
  (let [build-id "dev"
        opts (-> kf/default-opts
                 (update :default-target merge {:reference-directory "test-resources/kamera"
                                                :screenshot-directory "target/kamera"}))]

    (kf/test-devcards build-id opts)))
```

The output will look something like this:

```clojure
Results

example.kamera-test
1 non-passing tests:

Fail in devcards-test
#!/example.another_core_test
example.another_core_test.png has diverged from reference by 0.020624, please compare
Expected: test-resources/kamera/example.another_core_test.png
Actual: target/kamera/example.another_core_test.png
Difference: target/kamera/example.another_core_test-difference.png
expected: (< metric metric-threshold)

  actual: (not (< 0.020624 0.01))
```

![](doc/example.another_core_test.png?raw=true)
![](doc/example.another_core_test-difference.png?raw=true)
![](doc/example.core_test.png?raw=true)

## Options

```clojure
{:path-to-imagemagick nil                        ;; directory where binaries reside on linux, or executable on windows
 :imagemagick-timeout 2000                       ;; die if any imagemagick operation takes longer than this, in ms
 :default-target                                 ;; default options for each image comparison
   {:root "http://localhost:9500/devcards.html"  ;; the common root url where all targets can be found
    :metric-threshold 0.01                       ;; difference metric above which comparison fails
    :load-timeout 60000                          ;; max time in ms to wait for target url to load
    :reference-directory "test-resources/kamera" ;; directory where reference images are store
    :screenshot-directory "target/kamera"        ;; diredtory where screenshots and diffs should be saved
    :normalisations [:trim :crop]}               ;; normalisations to apply to expected and actual images before comparison, in order of application
 :normalisation-fns {:trim trim-fn               ;; normalisation functions, add any that you wish to use - see trim and crop for signature
                     :crop crop-fn}
 :chrome-options dcd/default-options             ;; options passed to chrome, letting you turn headless on/off etc
```

## Development

Start a normal Clojure REPL

[![CircleCI](https://circleci.com/gh/oliyh/kamera.svg?style=svg)](https://circleci.com/gh/oliyh/kamera)

## License

Copyright © 2018 oliyh

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
