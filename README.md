# DHIS2-to-RapidPro

![Build Status](https://github.com/dhis2/integration-dhis2-rapidpro/workflows/CI/badge.svg)

DHIS2-to-RapidPro is a stand-alone Java solution that integrates DHIS2 with RapidPro. It provides:

* Routine synchronisation of RapidPro contact to DHIS2 users
* Saving of reports from RapidPro to DHIS2

## Requirements

* Java 11

## Setup

1. Ensure each relevant data set data element has a code associated with it in DHIS2 and that the codes do not start with numbers.
2. Save each incoming data value in the RapidPro flow to a result. The result's name must be the code of the data value's corresponding data element in DHIS2.
3. Create a webhook call node in the RapidPro flow that dispatches the results to the DHIS2 connector:
   - HTTP method is a POST
   - URL points to the address the connector is listening on as configured in `http.endpoint.uri` parameter
   - HTTP body must have the `dhis2_organisation_unit_id` property in the `contact` object and the `data_set_id` property in the `flow object`. For example:
    ```
    @(json(object(
       "contact", object(
         "uuid", contact.uuid, 
         "name", contact.name, 
         "urn", contact.urn,
         "dhis2_organisation_unit_id", contact.dhis2_organisation_unit_id
       ),
       "flow", object(
         "uuid", run.flow.uuid, 
         "name", run.flow.name,
         "data_set_id", "qNtxTrp56wV"
       ),
       "results", foreach_value(results, extract_object, "value", "category")
    )))
    ```

## Getting Started

### *nix Usage Example

```shell
./dhis2-to-rapidpro.jar --dhis2.api.url=https://play.dhis2.org/2.37.2/api --dhis2.api.username=admin --dhis2.api.password=district --rapidpro.api.url=https://rapidpro.dhis2.org/api/v2 --rapidpro.api.token=3048a3b9a04c1948aa5a7fd06e7592ba5a17d3d0 --report.period.type=weekly
```

### Windows Usage Example

```shell
java -jar dhis2-to-rapidpro.jar --dhis2.api.url=https://play.dhis2.org/2.37.2/api --dhis2.api.username=admin --dhis2.api.password=district --rapidpro.api.url=https://rapidpro.dhis2.org/api/v2 --rapidpro.api.token=3048a3b9a04c1948aa5a7fd06e7592ba5a17d3d0 --report.period.type=weekly
```

## Config

By order of precedence, a config property can be specified:

1. as a command-line argument (e.g., `--dhis2.api.username=admin`)
2. as an OS environment variable (e.g., `export DHIS2_API_USERNAME=admin`)
3. in a key/value property file called `application.properties` or a YAML file named `application.yml`

| Config Name                | Description                                                                                                                                                                   | Default Value                             | Example Value                |
|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------|------------------------------|
| `http.endpoint.uri`        | The address the application will bind to for accepting HTTP requests.                                                                                                         | `http://localhost:8081/rapidProConnector` | `http://localhost:8080/acme` |
| `sync.schedule.expression` | Cron expression for triggering the copying of DHIS2 users to RapidPro as contact. By default, execution is kicked off at midnight every day.                                  | `0 0 0 * * ?`                             | `0 0 12 * * ?`               |
| `sync.dhis2.users`         | Whether to copy DHIS2 users as contacts to RapidPro.                                                                                                                          | `true`                                    | `false`                      |
| `report.period.type`       | Period type to use for the data value set sent to DHIS2. Must be set to one of the following: `daily`, `weekly`, `monthly`, `bi_monthly`, `six_monthly`, `financial_year_nov` |                                           | `weekly`                     |
| `report.period.offset`     | Relative period to add or subtract from the current reporting period sent to DHIS2.                                                                                           | `0`                                       | `-1`                         |
| `org.unit.id.scheme`       | By which field organisation units are identified.                                                                                                                             | `ID`                                      | `CODE`                       |