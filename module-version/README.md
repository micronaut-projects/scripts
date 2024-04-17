Given a module name and a Micronaut release version, return the version of that module in that release.

```shell
$ module-version micronaut-security --micronaut 4.3.2

Searching platform for micronaut-security in release 4.3.2
Version: 4.6.3
```

It is also possible to search the branch on Github for unreleased versions:

```shell
$ module-version micronaut-security --micronaut 4.4.x

Searching platform for micronaut-security on branch 4.4.x
Version: 4.7.0
```
