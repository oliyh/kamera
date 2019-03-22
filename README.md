# kamera

Visual testing tools for Clojure with [figwheel-main](https://github.com/bhauman/figwheel-main)
and [devcards](https://github.com/bhauman/devcards) integration.

Give kamera some reference images and your devcards build and get automatic screenshots and comparisons to your references of all your devcards.
If you don't use figwheel or devcards, kamera can accept [a list of urls](#core-api) for you to roll your own.

![](doc/juxtaposed.png?raw=true)

[![Clojars Project](https://img.shields.io/clojars/v/kamera.svg)](https://clojars.org/kamera)

- [Why?](#why)
- [Prerequesites](#prerequesites)
- [Usage](#Usage)
  - [Figwheel + devcards](#figwheel--devcards)
  - [Core API](#core-api)
- [Options](#options)
- [Normalisation](#normalisation)
- [Example use cases](#example-use-cases)

## Why?

When data is represented visually for a human to view you must take to present it intuitively, accessibly
and beautifully. This requires skill, time and above all human judgement.

Once achieved you want to ensure it does not suffer regressions. kamera is a library designed to
help you capture and compare screenshots of your application, failing if there is too much divergence between an expected
reference image and the current display.

The best way to test visual representation is to create [devcards](https://github.com/bhauman/devcards)
which you can use to display components in as many states as possible. If you ensure you separate rendering from
business logic you can ensure that refactoring will not affect them and prevent them becoming brittle - I outlined this approach
in a [blog post for JUXT](https://juxt.pro/blog/posts/cljs-apps.html).

## Prerequesites

kamera uses ImageMagick for image processing and Chrome to capture screenshots.
By default it looks for them on the path, but you can supply paths if they reside somewhere else - see the [options](#options).
kamera lets you choose the metric for image comparison - [read more about the choices here](https://imagemagick.org/script/command-line-options.php#metric).

## Usage

### figwheel + devcards

_See the [example project](https://github.com/oliyh/kamera/tree/master/example) for a full working example._

The following assumes a figwheel-main build called `dev` which has devcards that you view at `/devcards.html`.

If you have the following devcards files:

```bash
test
└── example
    ├── another_core_test.cljs
    └── core_test.cljs
```

... and a directory populated with reference images, named in the same way:

```
test-resources
└── kamera
    ├── example.another_core_test.png
    └── example.core_test.png
```

_You can generate these images initially by running kamera and copying the 'actual' files from the target directory into your reference directory_

... you can get kamera to screenshot the devcards and compare with the corresponding reference images with the following:

```clojure
(ns example.devcards-test
  (:require [kamera.devcards :as kd]
            [clojure.test :refer [deftest testing is]]))

(deftest devcards-test
  (kd/test-devcards "dev" kd/default-opts))
```

The output will look like this:

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

The target directory will contain an expected, actual and difference image for every devcard.
The three files referenced above will look like this:

![](doc/juxtaposed.png?raw=true)

### Core API

If you don't use figwheel or devcards you can still use kamera to take screenshots and compare them to reference images.

You will have to provide a list of "targets" for kamera to test.
Each target must provide a `:url` and `:reference-file` and can override any setting from the `:default-target` [options](#options).

```clojure
(require '[kamera.core :as k])

(k/run-tests [{:url "/"
               :reference-file "home.png"}
              {:url "/preferences"
               :reference-file "preferences.png"
               :metric-threshold 0.2}
              {:url "/help"
               :reference-file "help.png"
               :metric "RMSE"
               :normalisations [:trim]}]

             (assoc-in k/default-opts [:default-target :root] "http://localhost:9500"))
```

## Options

```clojure
{:default-target                                 ;; default options for each image comparison
   {:root "http://localhost:9500/devcards.html"  ;; the common root url where all targets can be found
    :metric "mae"                                ;; the imagemagick metric to use for comparison
                                                 ;; see https://imagemagick.org/script/command-line-options.php#metric

    :metric-threshold 0.01                       ;; difference metric above which comparison fails
    :load-timeout 60000                          ;; max time in ms to wait for target url to load
    :reference-directory "test-resources/kamera" ;; directory where reference images are store
    :screenshot-directory "target/kamera"        ;; directory where screenshots and diffs should be saved
    :ready? (fn [session] ... )                  ;; predicate that should return true when screenshot can be taken
                                                 ;; see element-exists? as an example
    :normalisations [:trim :crop]}               ;; normalisations to apply to images before comparison, in order of application

 :normalisation-fns                              ;; normalisation functions, add your own if desired
   {:trim trim-images
    :crop crop-images}

 :imagemagick-options
   {:path nil                                    ;; directory where binaries reside on linux, or executable on windows
    :timeout 2000}                               ;; kill imagemagick calls that exceed this time, in ms

 :chrome-options dcd/default-options             ;; options passed to chrome, letting you turn headless on/off etc
                                                 ;; see https://github.com/oliyh/doo-chrome-devprotocol
}
```

### devcards options

A few additional options exist if you are using the `kamera.devcards` namespace:

```clojure
{:devcards-options
  {:path "devcards.html"            ;; the relative path to the page where the devcards are hosted
   :init-hook (fn [session] ... )   ;; function run before attempting to scrape targets
   :on-targets (fn [targets] ... )} ;; function called to allow changing the targets before the test is run
}
```

## Normalisation

When comparing images ImageMagick requires both input images to be the same dimensions.
They can easily differ when changes are made to your application, across operating systems or browser versions.
Normalisation is the process of resizing both the expected and the actual images in a way that keeps the images lined up with one another
for the best comparison.

The built-in normalisations are `trim` and `crop`.
The former cuts out whitespace around image content and the latter crops the image canvas.
They are run sequentially and each stage is output to the target directory, giving a set of images as follows:

```bash
kamera
├── example.core_test.actual.png
├── example.core_test.actual.trimmed.cropped.png
├── example.core_test.actual.trimmed.png
├── example.core_test.expected.difference.png
├── example.core_test.expected.png
├── example.core_test.expected.trimmed.cropped.png
└── example.core_test.expected.trimmed.png
```

You can override the normalisations to each image, perhaps adding a `resize`:

```clojure
{:normalisations [:trim :resize :crop]}
```

And provide the `resize` function in the options map:

```clojure
{:normalisation-fns {:trim   trim-fn
                     :crop   crop-fn
                     :resize resize-fn}}
```

The signature of `resize` should look like this:

```clojure
(defn resize-images [^File expected ^File actual opts])
```

And it should return `[expected actual]`. See the existing `trim` and `crop` functions for inspiration.

## Example use cases

Here are some example use cases you may wish to consider in addition to the standard ones given above:

### Desktop / tablet / mobile testing

```clojure
(ns example.devcards-test
  (:require [kamera.devcards :as kd]
            [clojure.test :refer [deftest testing is]]))

(deftest desktop-test
  (kd/test-devcards
   "dev"
   (-> kd/default-opts
       (update :default-target merge
               {:reference-directory "test-resources/kamera/desktop"}))))

(deftest tablet-test
  (kd/test-devcards
   "dev"
   (-> kd/default-opts
       (update :default-target merge
               {:reference-directory "test-resources/kamera/tablet"})
       (assoc-in [:chrome-options :chrome-args] ["--headless" "--window-size=1024,768"]))))

(deftest mobile-test
  (kd/test-devcards
   "dev"
   (-> kd/default-opts
       (update :default-target merge
               {:reference-directory "test-resources/kamera/mobile"})
       (assoc-in [:chrome-options :chrome-args] ["--headless" "--window-size=800,600"]))))
```

### Spot comparison during a webdriver test

```clojure
(ns example.devcards-test
  (:require [kamera.core :as k]
            [clojure.test :refer [deftest testing is]]))

(deftest my-user-acceptance-test
  (let [driver (init-driver {:host "localhost" :port 9500})
        k-opts (assoc-in k/default-opts [:default-target :root] "http://localhost:9500")]

    ;;;  webdriver stuff happens ...

    (navigate! driver "/")

    (k/run-test {:url (.getUrl driver)
                 :reference-file "homepage-with-cookies-banner.png"}
                k-opts)

    (click! driver "#accept-cookies")

    (k/run-test {:url (.getUrl driver)
                 :reference-file "homepage-cookies-accepted.png"}
                k-opts)

    (.quit driver)))

```

## Development

Start a normal Clojure REPL

[![CircleCI](https://circleci.com/gh/oliyh/kamera.svg?style=svg)](https://circleci.com/gh/oliyh/kamera)

## License

Copyright © 2018 oliyh

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
