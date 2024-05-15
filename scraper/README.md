## Scraper

This uses Geb to scrape the https://micronaut.io site and saves the html, images, css and javascript to a `site` directory.

This output directory can be changed via the `-t` or `--target` argument.

### Requirements

- Requires Java 21
- Uses the Firefox driver, so requires Firefox

### Options

```shell
❯ ./gradlew run --args="--help"

> Task :run
Usage: scraper [-hV] [-t=<targetFolder>]
Scrape the micronaut.io website into a local folder
  -h, --help      Show this help message and exit.
  -t, --target=<targetFolder>
                  target output folder
  -V, --version   Print version information and exit.
```
### Example invocation

Writing the output to a local directory `scrape-output`:

```shell
❯ ./gradlew run --args="-t scrape-output"

> Task :run
Will save to scrape-output
Visiting  ==> index.html
Downloading https://micronaut.io/wp-content/cache/wpo-minify/1715696124/assets/wpo-minify-header-f5440c18.min.js to scrape-output/wp-content/cache/wpo-minify/1715696124/assets/wpo-minify-header-f5440c18.min.js
Downloading https://micronaut.io/wp-content/cache/wpo-minify/1715696124/assets/wpo-minify-footer-37e30695.min.js to scrape-output/wp-content/cache/wpo-minify/1715696124/assets/wpo-minify-footer-37e30695.min.js
Downloading https://micronaut.io/wp-content/uploads/2020/11/MIcronautLogo_Horizontal.svg to scrape-output/wp-content/uploads/2020/11/MIcronautLogo_Horizontal.svg
Downloading https://micronaut.io/wp-content/uploads/2020/11/Rockets.svg to scrape-output/wp-content/uploads/2020/11/Rockets.svg
Downloading https://micronaut.io/wp-content/uploads/2021/01/PlygotFramework.svg to scrape-output/wp-content/uploads/2021/01/PlygotFramework.svg
Downloading https://micronaut.io/wp-content/uploads/2021/01/NativelyCloudN.svg to scrape-output/wp-content/uploads/2021/01/NativelyCloudN.svg
Downloading https://micronaut.io/wp-content/uploads/2021/01/FastDataC.svg to scrape-output/wp-content/uploads/2021/01/FastDataC.svg
Downloading https://micronaut.io/wp-content/uploads/2021/01/SmoothLearningC.svg to scrape-output/wp-content/uploads/2021/01/SmoothLearningC.svg
Downloading https://micronaut.io/wp-content/uploads/2021/01/FastEasyT.svg to scrape-output/wp-content/uploads/2021/01/FastEasyT.svg
...
...
...
```