# DHIS2-to-RapidPro

![Build Status](https://github.com/dhis2/integration-dhis2-rapidpro/workflows/CI/badge.svg)

## Requirements

* Java 11

## Getting Started

### *nix Usage Example

```shell
./dhis2-to-rapidpro.jar --dhis2.api.url=https://play.dhis2.org/2.37.2/api --dhis2.api.username=admin --dhis2.api.password=district --rapidpro.api.url=https://rapidpro.dhis2.org/api/v2 --rapidpro.api.token=3048a3b9a04c1948aa5a7fd06e7592ba5a17d3d0
```

### Windows Usage Example

```shell
java -jar dhis2-to-rapidpro.jar --dhis2.api.url=https://play.dhis2.org/2.37.2/api --dhis2.api.username=admin --dhis2.api.password=district --rapidpro.api.url=https://rapidpro.dhis2.org/api/v2 --rapidpro.api.token=3048a3b9a04c1948aa5a7fd06e7592ba5a17d3d0
```