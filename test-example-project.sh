#!/usr/bin/env bash

cd example

lein test

if [ $? -eq 0 ]; then
    echo "Example project should not have passed, failing";
    exit 1;
else
    echo "Example project failed as expected"
fi
