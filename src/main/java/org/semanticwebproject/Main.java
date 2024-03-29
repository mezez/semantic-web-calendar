package org.semanticwebproject;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.CalendarComponent;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;
import org.apache.jena.atlas.json.io.JSONMaker;
import org.apache.jena.atlas.json.io.parser.JSONParser;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.datatypes.xsd.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static spark.Spark.*;

import static org.semanticwebproject.lib.Constants.*;
import static org.semanticwebproject.lib.Helpers.*;

public class Main {

    //PREFIXES
    public static final String SCHEMA_ORG_PREFIX = "http://schema.org/";
    public static final String W3_LDP_PREFIX = "http://www.w3.org/ns/ldp#";
    public static final String OWL_PREFIX = "http://www.w3.org/2002/07/owl#";
    public static final String EXAMPLE_PREFIX = "http://example.org/";
    public static final String EMSE_TERRITOIRE_PREFIX = "https://territoire.emse.fr/kg/emse/fayol/";

    public static void main(String[] args) throws Exception {

        /**
         * to remove CORS
         * */
        options("/*",
                (request, response) -> {

                    String accessControlRequestHeaders = request
                            .headers("Access-Control-Request-Headers");
                    if (accessControlRequestHeaders != null) {
                        response.header("Access-Control-Allow-Headers",
                                accessControlRequestHeaders);
                    }

                    String accessControlRequestMethod = request
                            .headers("Access-Control-Request-Method");
                    if (accessControlRequestMethod != null) {
                        response.header("Access-Control-Allow-Methods",
                                accessControlRequestMethod);
                    }

                    return "OK";
                });

        before((request, response) -> response.header("Access-Control-Allow-Origin", "*"));

        /**
         * API ENDPOINTS START
         * (Re-)Download, process and upload CPS2 ICS file found at $calendar_url to Territoire LDP
         *
         * request method: POST
         * request body: $calendar_url
         * content type: text
         *
         * $calendar_url = https://planning.univ-st-etienne.fr/jsp/custom/modules/plannings/anonymous_cal.jsp?resources=4222&projectId=1&calType=ical&firstDate=2022-08-22&lastDate=2023-08-20
         * */
        post("/download", (req, res) -> {
            downloadICS(req.body());
            boolean isValidShapeAndUploaded = readFile();
            if (isValidShapeAndUploaded) {
                return "success";
            } else {
                res.status(400);
                return "Error occurred on validation";
            }
        });


        /** Re-process and upload previously downloaded CPS2 ICS file (calendar.ics in project root directory) found at $calendar_url
         * to Territoire LDP
         *
         * request method: GET
         *
         * $calendar_url = https://planning.univ-st-etienne.fr/jsp/custom/modules/plannings/anonymous_cal.jsp?resources=4222&projectId=1&calType=ical&firstDate=2022-08-22&lastDate=2023-08-20
         * */
        get("/read", (req, res) -> {
            boolean isValidShapeAndUploaded = readFile();
            if (isValidShapeAndUploaded) {
                return "success";
            } else {
                res.status(400);
                return "Error occurred during shacl validation. Please ensure that the data conforms with the expected shape. See shacl_validation_shape.ttl and shacl_validation_shape_cps2_course.ttl files in project root directory";
            }
        });

        /**
         * Extract, process and upload events from alentoor.fr
         *
         * request method: POST
         * request body: $city_name
         * content type: text
         *
         * $city_name = eg saint-etienne | lyon | paris...
         * */
        post("/extract", (req, res) -> {
            String url = "https://www.alentoor.fr/" + req.body() + "/agenda";
            boolean isValidShape = fetchRDFFromUrl(url, req.body());
            if (isValidShape) {
                return "success";
            } else {
                res.status(400);
                return "Error occurred during shacl validation. Please ensure that the data conforms with the expected shape. See shacl_validation_shape.ttl and shacl_validation_shape_cps2_course.ttl files in project root directory";
            }
        });

        /**
         * Get events happening on a specific date e.g. 09-12-2022
         *
         * request method: POST
         * request body: year, month, day
         * content type: JSON
         *
         * year = eg 2022
         * month = eg 06 (ie june)
         * day = eg 09
         * */
        post("/get-events", (req, res) -> {
            JSONMaker jm = new JSONMaker();
            JSONParser.parseAny(new StringReader(req.body()), jm);
            JsonObject obj = jm.jsonValue().getAsObject();
            return (upcomingEventsByDate(obj.getString("year"), obj.getString("month"), obj.getString("day")));
        });

        /**
         * Add attendee to an event
         *
         * request method: POST
         * request body: eventURI, attendeeURI
         * content type: JSON
         *
         * eventURI: resource URI on territoire LDP, eg https://territoire.emse.fr/ldp/mieventcontainer/vevent-2
         * attendeeURI: eg http://example.com/mezIgnas/
         *
         * */
        post("/add-attendee", (req, res) -> {
            JSONMaker jm = new JSONMaker();
            JSONParser.parseAny(new StringReader(req.body()), jm);
            JsonObject obj = jm.jsonValue().getAsObject();
            addAttendeeToEvent(obj.getString("attendeeURI"), obj.getString("eventURI"));
            return "success";
        });

        /**
         * Get non-course events
         *
         * request method: GET
         * */
        get("/get-non-course-events", (req, res) -> {
            return (nonCourseEvents());
        });


        /**
         * Discover and Link Same Events
         *
         * request method: POST
         * request body: startDate, endDate, location
         * content type: JSON
         *
         * startDate: start datetime of events e.g. 2022-12-09T06:45:00Z
         * endDate: end datetime of events e.g. 2022-12-09T10:00:00Z
         * location: full iri of event location eg https://territoire.emse.fr/kg/emse/fayol/2ET/214
         *
         * */
        post("/discover-and-link-same-events", (req, res) -> {
            JSONMaker jm = new JSONMaker();
            JSONParser.parseAny(new StringReader(req.body()), jm);
            JsonObject obj = jm.jsonValue().getAsObject();
            List<String> events = linkSameEvents(obj.getString("startDate"), obj.getString("endDate"), obj.getString("location"));
            if (!events.isEmpty()) {
                return events;
            } else {
                return "No events found";
            }
        });

        /**
         * API ENDPOINTS END
         */


        /**
         * CONSOLE BASED EXECUTION/GUI START
         * */
        //download and read calendar file or read if necessary
        String action = getCommand();

        if (action.equals(DOWNLOAD_ICS_COMMAND) || action.equals(READ_COMMAND)) {
            if (action.equals(DOWNLOAD_ICS_COMMAND)) {
                String url = getUrl();
                downloadICS(url);
            }
            readFile();
        }

        if (action.equals(EXTRACT_COMMAND)) {
            String alentoorCity = getCity();
            String url = "https://www.alentoor.fr/" + alentoorCity + "/agenda";
            fetchRDFFromUrl(url, alentoorCity);
        }

        if (action.equals(ADD_ATTENDEE_COMMAND)) {
            List<String> attendeeDetails = getAttendeeDetails();

            addAttendeeToEvent(attendeeDetails.get(0), attendeeDetails.get(1));
        }

        if (action.equals(GET_EVENTS_COMMAND)) {
            List<String> dateDetails = getEventDate();

            System.out.println(upcomingEventsByDate(dateDetails.get(2), dateDetails.get(1), dateDetails.get(0)));
        }

        if (action.equals(GET_NON_COURSE_EVENTS_COMMAND)) {
            System.out.println(nonCourseEvents());
        }

        if (action.equals(LINK_COMMAND)) {
            List<String> eventDetails = getEventDetails();
            List<String> events = linkSameEvents(eventDetails.get(0), eventDetails.get(1), eventDetails.get(2));
            if (!events.isEmpty()) {
                System.out.println("Linking completed");
                System.out.println("_________________");
                System.out.println("");
                System.out.println("Linked Events::");
                System.out.println(events);
            } else {
                System.out.println("Events found");
            }
        }

        /**
         * CONSOLE BASED EXECUTION/GUI END
         * */


    }


    /**
     * LOADS calendar.ics file for processing calendar data and uploading to LDP
     * */
    public static boolean readFile() throws Exception {
        FileInputStream fin = new FileInputStream(CALENDAR_FILE_NAME);
        CalendarBuilder builder = new CalendarBuilder();
        Calendar calendar = builder.build(fin);
        return parseCalendarToRDF(calendar);
    }

    /**
     * Used for accepting command on what feature to execute by the user
     * */
    public static String getCommand() throws IOException {
        // Enter data using BufferReader
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in));

        // Reading data using readLine
        System.out.println();
        System.out.println("Note::::::::::::::::::::::::::::");
        System.out.println("________________________________");
        System.out.println();
        System.out.println("If you wish to use this application via API calls, see readme.md documentation for details.");
        System.out.println("To continue on  the console, follow the instruction below.");
        System.out.println("__________________________________________________________");
        System.out.println();
        System.out.println("Please enter a run command: download | read  | extract | get_events | add_attendee | get_non_course_events | link_same_events:");
        String command = reader.readLine().toUpperCase();

        while (!command.equals(DOWNLOAD_ICS_COMMAND) && !command.equals(READ_COMMAND) && !command.equals(ADD_ATTENDEE_COMMAND) && !command.equals(GET_EVENTS_COMMAND) && !command.equals(EXTRACT_COMMAND) && !command.equals(GET_NON_COURSE_EVENTS_COMMAND) && !command.equals(LINK_COMMAND)) {
            System.out.println("Command must be either of DOWNLOAD | READ | EXTRACT | GET_EVENTS| ADD_ATTENDEE | GET_NON_COURSE_EVENTS | LINK_SAME_EVENTS");
            command = reader.readLine().toUpperCase();
        }

        return command;
    }

    /**
     * Used for taking datetime input from user during console based execution by the user
     * */
    public static List<String> getEventDate() throws IOException {
        // Enter data using BufferReader
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in));

        // Reading data using readLine
        System.out.println("Please enter event date in dd-MM-yyyy format eg 09-12-2022 :");
        String date = reader.readLine();

        while (date.isEmpty()) {
            System.out.println("Date must be in the format of dd-MM-yyyy");
            date = reader.readLine().toUpperCase();
        }

        List<String> dateDetails = List.of(date.split("-"));
        System.out.println(dateDetails);

        return dateDetails;
    }

    /**
     * Used for taking event details input from user during console based execution
     * */
    public static List<String> getEventDetails() throws IOException {
        // Enter data using BufferReader
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in));

        // Reading data using readLine
        System.out.println("Please enter a event start datetime: eg 2022-12-09T07:45:00Z");
        String startDate = reader.readLine();

        while (startDate.isEmpty()) {
            System.out.println("start date is invalid");
            startDate = reader.readLine().toUpperCase();
        }

        System.out.println("Please enter a event end datetime: eg 2022-12-09T11:00:00Z");
        String endDate = reader.readLine();

        while (endDate.isEmpty()) {
            System.out.println("end date is invalid");
            endDate = reader.readLine();
        }

        System.out.println("Please enter event location iri: eg https://territoire.emse.fr/kg/emse/fayol/2ET/214");
        String location = reader.readLine();

        while (location.isEmpty()) {
            System.out.println("location is invalid");
            location = reader.readLine();
        }

        List<String> eventDetails = new ArrayList<String>();
        eventDetails.add(startDate.replaceAll("\\s+", ""));
        eventDetails.add(endDate.replaceAll("\\s+", ""));
        eventDetails.add(location.replaceAll("\\s+", ""));

        return eventDetails;
    }

    /**
     * Used for taking event attendee details (IRI) input from user during console based execution
     * */
    public static List<String> getAttendeeDetails() throws IOException {
        // Enter data using BufferReader
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in));

        // Reading data using readLine
        System.out.println("Please enter a event uri: eg https://territoire.emse.fr/ldp/mieventcontainer/vevent-2/");
        String eventUri = reader.readLine();

        while (eventUri.isEmpty()) {
            System.out.println("event url is invalid");
            eventUri = reader.readLine().toUpperCase();
        }

        System.out.println("Please enter a attendee uri: eg http://example.com/mezIgnas/");
        String attendeeUri = reader.readLine();

        while (attendeeUri.isEmpty()) {
            System.out.println("event url is invalid");
            attendeeUri = reader.readLine();
        }

        List<String> attendeeDetails = new ArrayList<String>();
        attendeeDetails.add(attendeeUri);
        attendeeDetails.add(eventUri);

        return attendeeDetails;
    }

    /**
     * Used for taking input on url of calendar to download from user during console based execution
     * */
    public static String getUrl() throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in));

        // Reading data using readLine
        System.out.println("Please enter a url to file (CPS2 ICS file):");
        System.out.println("Eg https://planning.univ-st-etienne.fr/jsp/custom/modules/plannings/anonymous_cal.jsp?resources=4222&projectId=1&calType=ical&firstDate=2022-08-22&lastDate=2023-08-20 :");

        String url = reader.readLine();

        while (url.isEmpty()) {
            System.out.println("Please enter a valid url");
            url = reader.readLine();
        }

        return url;
    }

    /**
     * Used for taking alentoor city name input for webpage extraction from user during console based execution
     * */
    public static String getCity() throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in));

        // Reading data using readLine
        System.out.println("Please enter an alentoor city name, eg saint-etienne, lyon :");

        String city = reader.readLine();

        while (city.isEmpty()) {
            System.out.println("Please enter a valid city");
            city = reader.readLine();
        }

        return city;
    }

    /**
     * Takes a Calendar object which is generated after parsing ICS file as input
     * Tries to create a container on the LDP (this will have no effect if a container already exists)
     * Iterates through the calendar list generated, creates a resource from each event and writes each resource to a temp file
     * Attempts to validate the file contents against a shacl shape and upload to ldp
     * */
    public static Boolean parseCalendarToRDF(Calendar calendar) throws Exception {
        List<CalendarComponent> calendarList = calendar.getComponentList().getAll();

        List<String> eventFileName = new ArrayList<String>();

        Integer eventCount = 1;

        Model model = ModelFactory.createDefaultModel();
        Resource eventsInfo = model.createResource("https://mines-saint-etienne.cps2.com/mycalendar");
        String eventName = CONTAINER_NAME_URL;

        Resource eventInfo = model.createResource(eventName);
        eventInfo.addProperty(RDF.type, W3_LDP_PREFIX + "BasicContainer");

        model.createStatement(eventInfo, RDF.type, eventsInfo);


        String tempFileName = CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME + "_container.ttl";
        eventFileName.add(tempFileName);

        FileWriter writer = new FileWriter(tempFileName);
        BufferedWriter bufferedWriter = new BufferedWriter(writer);

        model.write(bufferedWriter, "Turtle");
        bufferedWriter.close();
        boolean isValidShape = uploadTurtleFile(LDP_DESTINATION, true, 0, false);


        for (CalendarComponent calendarEvent : calendarList) {
            model = ModelFactory.createDefaultModel();

            //PROPERTIES
            final org.apache.jena.rdf.model.Property A_THING = model.createProperty("a");
            final org.apache.jena.rdf.model.Property DATE_TIME = model.createProperty(SCHEMA_ORG_PREFIX + "DateTime");
            final org.apache.jena.rdf.model.Property DATE_START = model.createProperty(SCHEMA_ORG_PREFIX + "startDate");
            final org.apache.jena.rdf.model.Property DATE_END = model.createProperty(SCHEMA_ORG_PREFIX + "endDate");
            final org.apache.jena.rdf.model.Property DATE_CREATED = model.createProperty(SCHEMA_ORG_PREFIX + "dateCreated");
            final org.apache.jena.rdf.model.Property DATE_MODIFIED = model.createProperty(SCHEMA_ORG_PREFIX + "dateModified");
            final org.apache.jena.rdf.model.Property SUMMARY = model.createProperty(EXAMPLE_PREFIX + "summary");
            final org.apache.jena.rdf.model.Property LOCATION = model.createProperty(SCHEMA_ORG_PREFIX + "location");
            final org.apache.jena.rdf.model.Property DESCRIPTION = model.createProperty(SCHEMA_ORG_PREFIX + "description");
            final org.apache.jena.rdf.model.Property IDENTIFIER = model.createProperty(SCHEMA_ORG_PREFIX + "identifier");
            final org.apache.jena.rdf.model.Property SEQUENCE = model.createProperty(EXAMPLE_PREFIX + "sequence");

            eventName = calendarEvent.getName();
            List<Property> eventDetails = calendarEvent.getProperties();

            //loop through event details and form RDF

            eventInfo = model.createResource(eventName.substring(0, 1).toLowerCase() + eventName.substring(1) + "-" + eventCount.toString());


            eventInfo.addProperty(RDF.type, model.createResource(SCHEMA_ORG_PREFIX + "Event"));
            eventInfo.addProperty(RDF.type, model.createResource(SCHEMA_ORG_PREFIX + "Course"));

            for (Property eventDetail : eventDetails) {
                String detailName = eventDetail.getName();


                switch (detailName) {
                    case _DTSTAMP ->
                            eventInfo.addProperty(DATE_TIME, model.createTypedLiteral(createDateTimeObject(eventDetail.getValue())));
                    case _DTSTART ->
                            eventInfo.addProperty(DATE_START, model.createTypedLiteral(createDateTimeObject(eventDetail.getValue())));
                    case _DTEND ->
                            eventInfo.addProperty(DATE_END, model.createTypedLiteral(createDateTimeObject(eventDetail.getValue())));
                    case _SUMMARY -> eventInfo.addProperty(SUMMARY, model.createTypedLiteral(eventDetail.getValue()));
                    case _LOCATION -> {
                        eventInfo.addProperty(LOCATION, model.createResource(convertLocationToTerritoireIRI(eventDetail.getValue(), EMSE_TERRITOIRE_PREFIX)));
                    }
                    case _DESCRIPTION ->
                            eventInfo.addProperty(DESCRIPTION, model.createTypedLiteral(eventDetail.getValue()));
                    case _UID -> eventInfo.addProperty(IDENTIFIER, model.createTypedLiteral(eventDetail.getValue()));
                    case _CREATED ->
                            eventInfo.addProperty(DATE_CREATED, model.createTypedLiteral(createDateTimeObject(eventDetail.getValue())));
                    case _LAST_MODIFIED ->
                            eventInfo.addProperty(DATE_MODIFIED, model.createTypedLiteral(createDateTimeObject(eventDetail.getValue())));
                    case _SEQUENCE ->
                            eventInfo.addProperty(SEQUENCE, model.createTypedLiteral(Integer.parseInt(eventDetail.getValue())));
                }
            }

            model.createStatement(eventInfo, RDF.type, eventsInfo);

            tempFileName = CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME + "-" + eventCount.toString() + ".ttl";

            eventCount++;

            writer = new FileWriter(tempFileName);
            bufferedWriter = new BufferedWriter(writer);

            model.write(bufferedWriter, "Turtle");
            bufferedWriter.close();
        }


        int cc = 1;
        while (cc < eventCount) {
            isValidShape = uploadTurtleFile(LDP_DESTINATION, false, cc, true);
            cc++;

        }

        System.out.println("Generated output has been uploaded to defined DB: Fuseki or LDP");
        System.out.println("::::::::::::::::::::");
        return isValidShape;
    }

    /**
     * parses temp files containing json ld data into jena models, creates resources and writes them to temp files
     * then attempts to validate the file contents against shacl a shape and upload to ldp
     * */
    public static boolean parseJSONLDToRDF(Integer numberOfFiles) throws Exception {
        int count = 1;
        boolean isValidShape = false;
        while (count < numberOfFiles) {
            //read json ld file
            Model model = ModelFactory.createDefaultModel();
            model.read(FETCHED_JSON_LD_TEMP_NAME + "-" + count + ".jsonld");

            //write data to turtle file
            writeModelToFIle(CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME + "-" + count + ".ttl", model);

            //DELETE TEMP FILE HERE
            try {
                Files.deleteIfExists(Paths.get(FETCHED_JSON_LD_TEMP_NAME + "-" + count + ".jsonld"));
            } catch (Exception ex) {
//                System.out.println("file probably still in use");
            }

            //upload to ldp
            isValidShape = uploadTurtleFile(LDP_DESTINATION, false, count, false);
            count++;
        }
        System.out.println("Generated output has been uploaded to defined DB: Fuseki or LDP");
        System.out.println("::::::::::::::::::::");
        return isValidShape;
    }

    public static void mergeFiles(List<String> fileNames, String outputFileName) throws IOException {
        PrintWriter pw = new PrintWriter(outputFileName);
        for (String fileName : fileNames) {
            BufferedReader br = new BufferedReader(new FileReader(fileName));
            String line = br.readLine();
            // loop to copy each line of file
            while (line != null) {
                pw.println(line);
                line = br.readLine();
            }
            br.close();
            try {
                Files.deleteIfExists(Paths.get(fileName));
            } catch (Exception ex) {
//                System.out.println("file probably still in use");
            }
        }

        pw.flush();
        pw.close();

    }

    /**
     * uploads a resource to ldp, either a container or a container child on successful validation against shacl shape
     * */
    public static boolean uploadTurtleFile(String destination, Boolean isContainer, Integer count, Boolean isCPS2Event) throws Exception {
        boolean isValidShape = false;

        //USED FOR FUSEKI TESTS
        if (destination.equals(FUSEKI_DESTINATION)) {
            try (RDFConnection conn = RDFConnectionFactory.connect(LOCAL_FUSEKI_SERVICE_URL)) {
                conn.put(CALENDAR_OUTPUT_TURTLE_FILE_NAME);
            }
        } else {
            //upload to territoire
            try {

                HttpPost post = new HttpPost(isContainer ? TERRITOIRE_SERVICE_URL : TERRITOIRE_CONTAINER_SERVICE_URL);
                post.addHeader("Authorization", AUTH_TOKEN);
                post.addHeader("Accept", "text/turtle");
                post.addHeader("Content-Type", "text/turtle");
                post.addHeader("Link", "<http://www.w3.org/ns/ldp#BasicContainer>; rel=\"type\"");
                post.addHeader("Prefer", "http://www.w3.org/ns/ldp#Container; rel=interaction-model");
//            post.addHeader("Slug", CONTAINER_NAME);
                post.addHeader("Slug", "testtest2");

                String requestBody;
                String fileName = "";
                if (isContainer) {
                    requestBody = Files.readString(Path.of(CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME + "_container.ttl"), StandardCharsets.UTF_8);
                    fileName = CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME + "_container.ttl";

                } else {
                    //container child
                    requestBody = Files.readString(Path.of(CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME + "-" + count.toString() + ".ttl"), StandardCharsets.UTF_8);
                    fileName = CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME + "-" + count.toString() + ".ttl";

                }
                //validate shape
                isValidShape = validateWithSHACL(fileName, false);
                if (!isValidShape) {
                    System.out.println("File: " + CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME + "-" + count.toString() + ".ttl");
                    System.out.println("Invalid events shape. See log file: in shacl-validation-reports folder for details");
                }

                if (isCPS2Event) {
                    isValidShape = validateWithSHACL(fileName, true);
                    if (!isValidShape) {
                        System.out.println("File: " + CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME + "-" + count.toString() + ".ttl");
                        System.out.println("Invalid events shape. See log file: in shacl-validation-reports folder for details");
                    }
                }

                System.out.println(requestBody);
                if (isValidShape) {
                    StringEntity requestBodyEntity = new StringEntity(requestBody);
                    post.setEntity(requestBodyEntity);
                }

                CloseableHttpClient httpClient = HttpClients.createDefault();
                CloseableHttpResponse response = httpClient.execute(post);

                //DELETE TEMP FILE HERE
                try {
                    Files.deleteIfExists(Paths.get(isContainer ? CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME + "_container.ttl" : CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME + "-" + count.toString() + ".ttl"));
                } catch (Exception ex) {
//                    System.out.println("file probably still in use");
                }
                System.out.println(EntityUtils.toString(response.getEntity()));
                return isValidShape;
            } catch (Exception e) {
                System.out.println(e.getMessage());
                throw new Exception(e);
            }
        }
        return isValidShape;
    }

    public static void deleteRemoteResource(String url) throws Exception {
        try {
            HttpDelete delete = new HttpDelete(url);
            delete.addHeader("Authorization", AUTH_TOKEN);
            System.out.println("Event at: " + url + " was deleted");
            CloseableHttpClient httpClient = HttpClients.createDefault();
            httpClient.execute(delete);

        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new Exception(e);
        }
    }

    /**
     * Converts datetime string to XSDDateTimeObject
     * */
    public static XSDDateTime createDateTimeObject(String dateTimeString) throws ParseException {
        String year = dateTimeString.substring(0, 4);
        String month = dateTimeString.substring(4, 6);
        String day = dateTimeString.substring(6, 8);
        String hour = dateTimeString.substring(9, 11);
        Integer hourInt = Integer.parseInt(hour) + 1;
        hour = hourInt.toString();
        String minute = dateTimeString.substring(11, 13);
        String second = dateTimeString.substring(13, 15);

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy;HH:mm:ss");

        Date date = simpleDateFormat.parse(day + "-" + month + "-" + year + ";" + hour + ":" + minute + ":" + second);
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTime(date);
        return new XSDDateTime(calendar);
    }

    /**
     * Retrieves and attendee resource from ldp, creates an attendee iri from input string and adds to the event resource
     * Then saves the updated content in the ldp
     * */
    public static void addAttendeeToEvent(String attendeeURI, String eventUrl) throws Exception {
        //fetch event
        HttpGet get = new HttpGet(eventUrl);
        get.addHeader("Authorization", AUTH_TOKEN);

        CloseableHttpResponse response;
        String eTagHeader;
        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            response = httpClient.execute(get);

            eTagHeader = response.getFirstHeader("ETag").getValue();

        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new Exception(e);
        }

        String data = EntityUtils.toString(response.getEntity());

        // write results to file
        String tempFileName = FETCHED_RESOURCE_TEMP_NAME; //REFACTOR TO include random numbers

        FileWriter writer = new FileWriter(tempFileName);
        BufferedWriter bufferedWriter = new BufferedWriter(writer);

        bufferedWriter.write(data);
        bufferedWriter.close();

        //read file into jena and update or delete and repost
        Model model = ModelFactory.createDefaultModel();
        model.read(FETCHED_RESOURCE_TEMP_NAME);

        final org.apache.jena.rdf.model.Property ATTENDEE = model.createProperty(SCHEMA_ORG_PREFIX + "attendee");
        model.getResource(eventUrl).addProperty(ATTENDEE, model.createResource(attendeeURI));

        // list the statements in the Model
//        StmtIterator iter = model.listStatements();
//
//        while (iter.hasNext()) {
//            Statement stmt      = iter.nextStatement();  // get next statement
//            Resource  subject   = stmt.getSubject();     // get the subject
//            org.apache.jena.rdf.model.Property predicate = stmt.getPredicate();   // get the predicate
//            RDFNode   object    = stmt.getObject();      // get the object
//
//            System.out.print(subject.toString());
//            System.out.print(" " + predicate.toString() + " ");
//            if (object instanceof Resource) {
//                System.out.print(object.toString());
//            } else {
//                // object is a literal
//                System.out.print(" \"" + object.toString() + "\"");
//            }
//
//            System.out.println(" .");
//        }

        //write to file
        writer = new FileWriter(FETCHED_RESOURCE_TEMP_NAME);
        bufferedWriter = new BufferedWriter(writer);

        model.write(bufferedWriter, "Turtle");

        //push file online
        try {

            HttpPut put = new HttpPut(eventUrl);
            put.addHeader("Authorization", AUTH_TOKEN);
            put.addHeader("Accept", "text/turtle");
            put.addHeader("Content-Type", "text/turtle");
            put.addHeader("If-Match", eTagHeader);
            put.addHeader("Prefer", "http://www.w3.org/ns/ldp#RDFSource; rel=interaction-model");


            String requestBody = Files.readString(Path.of(FETCHED_RESOURCE_TEMP_NAME));

            StringEntity requestBodyEntity = new StringEntity(requestBody);
            put.setEntity(requestBodyEntity);


            CloseableHttpClient httpClient = HttpClients.createDefault();
            response = httpClient.execute(put);

            try {
                Files.deleteIfExists(Paths.get(FETCHED_RESOURCE_TEMP_NAME));
            } catch (Exception ex) {
//                System.out.println("file probably still in use");
            }

            System.out.println(response.toString());
            System.out.println("Attendee added to event:::::::::");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new Exception(e);
        }
    }

    /**
     * Uses a sparql query to retrieve events by date, returns the iri (url) to the events found
     * */
    public static String upcomingEventsByDate(String year, String month, String day) throws Exception {

        try {

            HttpPost post = new HttpPost(TERRITOIRE_SERVICE_URL);
            post.addHeader("Authorization", AUTH_TOKEN);
            post.addHeader("Content-Type", "application/sparql-query");


            String requestBody = "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
                    "PREFIX ex: <http://example.org/>\n" +
                    "PREFIX schema: <http://schema.org/>\n" +
                    "\n" +
                    "SELECT ?sub WHERE {\n" +
                    "  ?sub ?pred ?obj;\n" +
                    "  schema:startDate ?startDate;\n" +
                    "  schema:endDate ?endDate.\n" +
                    "  FILTER(xsd:dateTime(?startDate) >= \"" + year + "-" + month + "-" + day + "T00:00:00Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)\n" +
                    "  FILTER(xsd:dateTime(?endDate) <= \"" + year + "-" + month + "-" + day + "T23:59:59Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)\n" +
                    "}";
            System.out.println("requestBody::::");
            System.out.println(requestBody);
            System.out.println();

            StringEntity requestBodyEntity = new StringEntity(requestBody);
            post.setEntity(requestBodyEntity);
            CloseableHttpClient httpClient = HttpClients.createDefault();
            CloseableHttpResponse response = httpClient.execute(post);

            String data = EntityUtils.toString(response.getEntity());

            //convert response to json
            JSONMaker jm = new JSONMaker();
            JSONParser.parseAny(new StringReader(data), jm);
            JsonObject obj = jm.jsonValue().getAsObject();

            List<JsonValue> bindings = obj.getObj("results").getArray("bindings").toList();
//        System.out.println(bindings);

            List<String> resourcesIRIs = new ArrayList<String>();
            for (JsonValue binding : bindings) {
                String bindingSubjectType = binding.getAsObject().getObj("sub").getString("type");
                String bindingSubjectValue = binding.getAsObject().getObj("sub").getString("value");
                if (bindingSubjectType.equals("uri")) {
                    if (!resourcesIRIs.contains(bindingSubjectValue)) {
                        resourcesIRIs.add(bindingSubjectValue);
                    }
                }

            }
            return resourcesIRIs.toString();

        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new Exception(e);
        }
    }

    /**
     * Uses a sparql query to retrieve events without schema:Course property, returns the iri (url) to the events found
     * */
    public static String nonCourseEvents() throws Exception {

        try {

            HttpPost post = new HttpPost(TERRITOIRE_SERVICE_URL);
            post.addHeader("Authorization", AUTH_TOKEN);
            post.addHeader("Content-Type", "application/sparql-query");


            String requestBody = "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
                    "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                    "PREFIX schema: <http://schema.org/>\n" +
                    "\n" +
                    "SELECT * WHERE {\n" +
                    "  ?sub a schema:Event.\n" +
                    "  FILTER (\n" +
                    "     !EXISTS {\n" +
                    "       ?sub a schema:Course\n" +
                    "     }\n" +
                    "   )\n" +
                    "}";

            System.out.println("requestBody::::");
            System.out.println(requestBody);
            System.out.println();

            StringEntity requestBodyEntity = new StringEntity(requestBody);
            post.setEntity(requestBodyEntity);
            CloseableHttpClient httpClient = HttpClients.createDefault();
            CloseableHttpResponse response = httpClient.execute(post);

            String data = EntityUtils.toString(response.getEntity());

            //convert response to json
            JSONMaker jm = new JSONMaker();
            JSONParser.parseAny(new StringReader(data), jm);
            JsonObject obj = jm.jsonValue().getAsObject();

            List<JsonValue> bindings = obj.getObj("results").getArray("bindings").toList();

            List<String> resourcesIRIs = new ArrayList<String>();
            for (JsonValue binding : bindings) {
                String bindingSubjectType = binding.getAsObject().getObj("sub").getString("type");
                String bindingSubjectValue = binding.getAsObject().getObj("sub").getString("value");
                if (bindingSubjectType.equals("uri")) {
                    if (!resourcesIRIs.contains(bindingSubjectValue)) {
                        resourcesIRIs.add(bindingSubjectValue);
                    }
                }

            }
            return resourcesIRIs.toString();

        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new Exception(e);
        }
    }

    /**
     * Uses a sparql query to retrieve events with the same start and end dates and times
     * */
    public static String discoverSameEvents(String startDate, String endDate, String location) throws Exception {

        try {

            HttpPost post = new HttpPost(TERRITOIRE_SERVICE_URL);
            post.addHeader("Authorization", AUTH_TOKEN);
            post.addHeader("Content-Type", "application/sparql-query");


            String requestBody = "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
                    "PREFIX ex: <http://example.org/>\n" +
                    "PREFIX schema: <http://schema.org/>\n" +
                    "\n" +
                    "SELECT ?sub WHERE {\n" +
                    "  ?sub ?pred ?obj;\n" +
                    "  schema:location <" + location + ">;\n" +
                    "  schema:startDate ?startDate;\n" +
                    "  schema:endDate ?endDate.\n" +
                    "  FILTER(xsd:dateTime(?startDate) = \"" + startDate + "\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)\n" +
                    "  FILTER(xsd:dateTime(?endDate) = \"" + endDate + "\"^^<http://www.w3.org/2001/XMLSchema#dateTime>)\n" +
                    "}";
            System.out.println("requestBody::::");
            System.out.println(requestBody);
            System.out.println();

            StringEntity requestBodyEntity = new StringEntity(requestBody);
            post.setEntity(requestBodyEntity);
            CloseableHttpClient httpClient = HttpClients.createDefault();
            CloseableHttpResponse response = httpClient.execute(post);

            return (EntityUtils.toString(response.getEntity()));

        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new Exception(e);
        }
    }


    /**
     * calls the discoverSameEvents method to retrieve events with the same start and end dates and times,
     * Links the results found using the owl:sameAs property
     * returns the iri (url) to the events found
     * */
    public static List<String> linkSameEvents(String startDate, String endDate, String location) throws Exception {

        String sameEvents = discoverSameEvents(startDate, endDate, location);

        //convert events to json
        JSONMaker jm = new JSONMaker();
        JSONParser.parseAny(new StringReader(sameEvents), jm);
        JsonObject obj = jm.jsonValue().getAsObject();

        List<JsonValue> bindings = obj.getObj("results").getArray("bindings").toList();
//        System.out.println(bindings);

        List<String> resourcesIRIs = new ArrayList<String>();
        for (JsonValue binding : bindings) {
            String bindingSubjectValue = binding.getAsObject().getObj("sub").getString("value");
            if (bindingSubjectValue.contains(TERRITOIRE_SERVICE_URL)) {
                if (!resourcesIRIs.contains(bindingSubjectValue)) {
                    resourcesIRIs.add(bindingSubjectValue);
                }
            }

        }

//        System.out.println(resourcesIRIs);


        for (String currentResourceIRI : resourcesIRIs) {

            //loop through other resources, fetch and add same as the current
            for (String resourceIRIExceptCurrent : resourcesIRIs) {
                if (!resourceIRIExceptCurrent.equals(currentResourceIRI)) {

                    HttpGet get = new HttpGet(resourceIRIExceptCurrent.trim());
                    get.addHeader("Authorization", AUTH_TOKEN);

                    CloseableHttpResponse response;
                    String eTagHeader;
                    try {
                        CloseableHttpClient httpClient = HttpClients.createDefault();
                        response = httpClient.execute(get);

                        eTagHeader = response.getFirstHeader("ETag").getValue();

                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                        throw new Exception(e);
                    }

                    String data = EntityUtils.toString(response.getEntity());

                    // write results to file
                    String tempFileName = FETCHED_RESOURCE_TEMP_NAME; //REFACTOR TO include random numbers

                    FileWriter writer = new FileWriter(tempFileName);
                    BufferedWriter bufferedWriter = new BufferedWriter(writer);

                    bufferedWriter.write(data);
                    bufferedWriter.close();

                    //read file into jena and update or delete and repost
                    Model model = ModelFactory.createDefaultModel();
                    model.read(FETCHED_RESOURCE_TEMP_NAME);

                    final org.apache.jena.rdf.model.Property SAME_AS = model.createProperty(OWL_PREFIX + "sameAs");
                    model.getResource(resourceIRIExceptCurrent).addProperty(SAME_AS, model.createResource(currentResourceIRI));

                    // list the statements in the Model
//        StmtIterator iter = model.listStatements();
//
//        while (iter.hasNext()) {
//            Statement stmt      = iter.nextStatement();  // get next statement
//            Resource  subject   = stmt.getSubject();     // get the subject
//            org.apache.jena.rdf.model.Property predicate = stmt.getPredicate();   // get the predicate
//            RDFNode   object    = stmt.getObject();      // get the object
//
//            System.out.print(subject.toString());
//            System.out.print(" " + predicate.toString() + " ");
//            if (object instanceof Resource) {
//                System.out.print(object.toString());
//            } else {
//                // object is a literal
//                System.out.print(" \"" + object.toString() + "\"");
//            }
//
//            System.out.println(" .");
//        }

                    //write to file
                    writer = new FileWriter(FETCHED_RESOURCE_TEMP_NAME);
                    bufferedWriter = new BufferedWriter(writer);

                    model.write(bufferedWriter, "Turtle");

                    //push file online
                    try {

                        HttpPut put = new HttpPut(resourceIRIExceptCurrent);
                        put.addHeader("Authorization", AUTH_TOKEN);
                        put.addHeader("Accept", "text/turtle");
                        put.addHeader("Content-Type", "text/turtle");
                        put.addHeader("If-Match", eTagHeader);
                        put.addHeader("Prefer", "http://www.w3.org/ns/ldp#RDFSource; rel=interaction-model");


                        String requestBody = Files.readString(Path.of(FETCHED_RESOURCE_TEMP_NAME));

                        StringEntity requestBodyEntity = new StringEntity(requestBody);
                        put.setEntity(requestBodyEntity);


                        CloseableHttpClient httpClient = HttpClients.createDefault();
                        response = httpClient.execute(put);

                        try {
                            Files.deleteIfExists(Paths.get(FETCHED_RESOURCE_TEMP_NAME));
                        } catch (Exception ex) {
//                            System.out.println("file probably still in use");
                        }

                        System.out.println(response.toString());
                        System.out.println(currentResourceIRI + " resource now added as ow:sameAs for " + resourceIRIExceptCurrent);
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                        throw new Exception(e);
                    }
                }
            }
        }
        return resourcesIRIs;
    }


    /**
     * Scrapes alentoor.fr from events information for the spefic city defined by user,
     * writes the json ld results retrieved to files and calls on another method to parse the files to turtle for processing and upload to ldp
     * */
    public static boolean fetchRDFFromUrl(String url, String alentoorCity) throws Exception {
        Document document = Jsoup.connect(url).get();

        String jsonLDString = "";

        final Integer[] count = {1};
        document.select("script").forEach(el ->
                {
                    String element = String.valueOf(el);
                    if (element.contains("application/ld+json")) {
                        element = element.replace("<script type=\"application/ld+json\">", "");
                        element = element.replace("</script>", "");
                        element = element.replace("@context\":\"http://schema.org", "@context\":\"http://schema.org/docs/jsonldcontext.json");

                        element = element.replaceFirst("https://www.alentoor.fr/agenda/", TERRITOIRE_CONTAINER_SERVICE_URL + alentoorCity + "-agenda-");

                        //UNIQUE TO ALENTOOR
//                        Pattern pattern = Pattern.compile("[a-zA-Z]+://[a-zA-Z]+\\.[a-zA-Z]+\\.[a-zA-Z]+/[a-zA-Z]+/[a-zA-Z]+/[a-zA-Z]+/[0-9]+");

                        Pattern pattern = Pattern.compile("\"@id\":\"https://territoire.emse.fr/ldp/mieventcontainer/" + alentoorCity + "-agenda-[0-9]+\"");
                        Matcher matcher = pattern.matcher(element);
                        if (matcher.find()) {

                            String strToReplace = matcher.group();
//                            System.out.println(strToReplace);
                            element = element.replaceFirst(strToReplace, strToReplace.substring(0, strToReplace.length() - 1) + "/\"");
                            System.out.println(element);
                        }

//                        rdfsItem.add(String.valueOf(element));
                        try {
                            writeStringToFile(element, FETCHED_JSON_LD_TEMP_NAME + "-" + count[0].toString() + ".jsonld");
                            count[0]++;
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
        );
//        document.select("script").forEach(System.out::println);
//        System.out.println(rdfsItem.size());
//        System.out.println(rdfsItem);
        return parseJSONLDToRDF(count[0]);
    }

}