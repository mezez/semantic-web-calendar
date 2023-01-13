# RDF Based Calendar

A semantic web project for creating and managing a calendar using RDF technologies.


A detailed guide and explanation of this project, codebase and features implementation is available online at https://docs.google.com/document/d/1ho0AaZf1vrmZeG5lASwfKAgwwbCUxJwa8RvnFNizOKg/edit?usp=sharing
## Execution

To run this application, run the **main.java** file. This will start up an API web server as well as a console interface

The application can be used via only one of the  two interfaces at each time, which ever approach selected first will remain the only available interface until the application is restarted

Documentation for the API endpoints can be found at https://documenter.getpostman.com/view/6071478/2s8Z76uUCw#7d397d02-d645-4a8a-81e5-d05efe8073a6

To use via console, simply follow the prompts displayed on the console interface.

## FRONT END

A frontend UI has been built for this application and be found at https://github.com/IgnasBarakauskas/sem-web-frond-end

## Features
This system consists of seven major features:

#### DOWNLOAD

This downloads a CPS2 master calendar events in ics format, processes/validates the content and uploads to territoire.emse.fr Linked Data Platform (LDP). A container is created automatically on the LDP by this command or the READ command in event that one does not already exist. The calendar data can be found at https://planning.univ-st-etienne.fr/jsp/custom/modules/plannings/anonymous_cal.jsp?resources=4222&projectId=1&calType=ical&firstDate=2022-08-22&lastDate=2023-08-20


#### READ

Reads, processes/validates and uploads an already downloaded or manually added ics file to the LDP.

#### EXTRACT

Downloads a page corresponding to a city (passed as an argument by the user) on alentoor.fr. The downloaded page is scraped and jsonld events data is extracted, processed, validated and uploaded to the LDP.

Note: It is assumed that you already have an LDP container existing to use the EXTRACT feature. So it is advisable to run the download/read commands in order to automatically create a container if one does not already exist

#### GET_EVENTS

Uses a sparql query to fetch events occurring on a specific date (as specific by the user) from the LDP.

#### ADD_ATTENDEE 

Retrieves a specific event from the LDP and adds and attendee property with and Identifier of the attendee. The updated event is saved on the LDP.

#### GET_NON_COURSE_EVENTS

Retrieves events from the LDP that do not have a schema:Course predicate defined on them

#### LINK_SAME_EVENTS

Finds events on the LDP that have the same location, start date and end date. It links these events together using the owl:sameAs property and re uploads the updated resources to the LDP.
