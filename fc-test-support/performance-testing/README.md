# Performance testing

## Description
The Gatling performance testing scripts for Gaia-X Catalogue Service.

This is an implementation of performance testing scripts for the Gaia-X Federation Services Lot 5 [Federated Catalogue](https://gitlab.com/gaia-x/data-infrastructure-federation-services/cat).

## Installation

For performance testing need to install the [Gatling](https://gatling.io/open-source/) testing tool and JDK. Gatling supports JDK versions: 8, 11 and 17.

1. Unzip the archive locally.

2. If you don't have the **_JAVA_HOME_** variable set by default or if you want to use a non-default version of java, open the **_bin/gatling.bat_** file and set the path manually.


    set JAVA_HOME=required_java_path

## Getting Started

Download the project data to the user-files folder. 

* The **_user-files/lib_** folder contains user libraries that are used in our gatling script code.
* The **_user-files/simulations_** folder contains gatling scripts and additional functionality for scripts to work.
* The **_user-files/resources_** folder contains resources files for gatling scripts.

In the **_user-files/resources/application.properties_** file you can find common script configurations. 

#### Gatling script configurations in the `application.properties` file:

* **users** - the number of users that will be injected to run the script:
`fc-performance-testing.sd.users`, `fc-performance-testing.query.users`
* **rampTime** - the time during which the users will be injected in the execution of the script:
`fc-performance-testing.sd.rampTime`, `fc-performance-testing.query.rampTime`
* **duringTime** - script execution time:
`fc-performance-testing.sd.duringTime`, `fc-performance-testing.query.duringTime`

## Running scripts

1. Run the _**bin/gatling.bat**_ file.
2. Select item `[1] Run the Simulation locally` from the list received in the console.
3. And then you can choose which of the scripts to run, for example:
* `[0] federatedcatalogue.QuerySimulation`
* `[1] federatedcatalogue.SelfDescriptionSimulation`
4. Next, you will see the optional input `Select run description (optional)`, which you can skip. After that, the execution of scripts will begin.
5. After completion in the **_results_** folder you can see the result of running the script.