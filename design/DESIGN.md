DHIS-to-RapidPro is a stand-alone Java solution that integrates DHIS2 with RapidPro. [DHIS2](https://dhis2.org/about/) is an open-source information system primarily used in the health domain while [RapidPro](https://rapidpro.github.io/rapidpro/) is an open-source workflow engine for running mobile-based services.

DHIS-to-RapidPro provides:

* Routine synchronisation of RapidPro contacts with DHIS2 users
* A webhook consumer to receive aggregate reports from RapidPro and transfer them to DHIS2 as data value sets
* Automated reminders to RapidPro contacts when their aggregate reports are overdue

DHIS-to-RapidPro is developed and maintained with the following design goals:

* Testable: it is easy to test end-to-end without requiring the 
* Flexible: DHIS-to-RapidPro is powered by Apache Camel
* Monitor & Manage: DHIS-to-RapidPro comes bundled
* Configurable: DHIS-to-RapidPro comes bundled with a variety swappable tools and libraries.

Extensible
Testable


## Sequence Diagrams


![auto-reminders](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.github.com/plantuml/plantuml-server/master/src/main/webapp/resource/test2diagrams.txt)

## Deployment Diagrams