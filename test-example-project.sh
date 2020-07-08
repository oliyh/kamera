#!/usr/bin/env bash

cd example

rm -rf ./target

lein test

if [ $? -eq 0 ]; then
    echo "Example project should not have passed, failing";
    exit 1;
else
    echo "Example project failed as expected"
    FILE=./target/kamera/results.edn
    if [ -f "$FILE" ]; then
        echo "Report $FILE generated as expected";
    else
        echo "Report $FILE was not generated, failing";
        exit 1;
    fi
fi
