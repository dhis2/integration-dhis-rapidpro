# DHIS2-to-RapidPro

![Build Status](https://github.com/dhis2/integration-dhis2-rapidpro/workflows/CI/badge.svg)

## Requirements

* Java 11

## Setup

1. Ensure each relevant data set data element has a code associated with it in DHIS2
2. Save each data value in the RapidPro flow to a result named like the corresponding data element code
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

## Configuration

### *nix Usage Example

```shell
./dhis2-to-rapidpro.jar --dhis2.api.url=https://play.dhis2.org/2.37.2/api --dhis2.api.username=admin --dhis2.api.password=district --rapidpro.api.url=https://rapidpro.dhis2.org/api/v2 --rapidpro.api.token=3048a3b9a04c1948aa5a7fd06e7592ba5a17d3d0
```

### Windows Usage Example

```shell
java -jar dhis2-to-rapidpro.jar --dhis2.api.url=https://play.dhis2.org/2.37.2/api --dhis2.api.username=admin --dhis2.api.password=district --rapidpro.api.url=https://rapidpro.dhis2.org/api/v2 --rapidpro.api.token=3048a3b9a04c1948aa5a7fd06e7592ba5a17d3d0
```