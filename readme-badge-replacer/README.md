This application is a command-line tool that automates the process of updating README badges in GitHub repositories. Here's a step-by-step breakdown of what it does:

1. It fetches all repositories from GitHub using the `GitubApi` class.
2. It filters out repositories whose names do not start with "micronaut-".
3. For each remaining repository, it clones the repository to the local machine.
4. It checks if the README.md file in the cloned repository contains the text "Revved up by Gradle Enterprise".
5. If the README.md file contains the specified text, it replaces it with a new badge for "Develocity".
6. After updating the README.md file, it commits the changes and pushes them to a new branch named "develocity-badge" in the repository.
7. Finally, it creates a pull request on GitHub to merge the changes from the "develocity-badge" branch to the "master" branch.

The selected line of code checks if the README.md file in the cloned repository contains the text "Revved up by Gradle Enterprise". If it does not, the repository is skipped.

### Usage:

Set up github token:
```
export GITHUB_TOKEN=xxx
```

Then build and run the utility:
```
./gradlew build
java -jar build/libs/readme-badge-replacer-0.1-all.jar
```