# JapiCmp failure report parser

## Building this tool

Run

```
./gradlew nativeCompile
```

Copy the native binary from `build/native/nativeCompile/japicmp` to a known location or in your path, ie:

```shell
cp build/native/nativeCompile/japicmp ~/bin/japicmp
```

## Use case

If you have a project that fails the japiCmp task, and you want to add the suppressions from the HTML report.

1. Run the task:
    ```
    ./gradlew japiCmp
    ```
1. Run this cli application and pipe the output to your clipboard:
    ```
    ~/bin/japicmp | pbcopy
    ```
1. Add these new suppressions to the bottom of `config/accepted-api-changes.json` 