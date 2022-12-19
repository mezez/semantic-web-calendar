package org.semanticwebproject;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.CalendarComponent;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.jena.base.Sys;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.datatypes.xsd.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.semanticwebproject.lib.Constants.*;
import static org.semanticwebproject.lib.Helpers.convertLocationToTerritoireIRI;
import static org.semanticwebproject.lib.Helpers.downloadICS;

public class Main {

    //PREFIXES
    public static final String SCHEMA_ORG_PREFIX = "https://schema.org/";
    public static final String W3_LDP_PREFIX = "http://www.w3.org/ns/ldp#";
    public static final String EXAMPLE_PREFIX = "http://example.org/";
    public static final String EMSE_TERRITOIRE_PREFIX = "https://territoire.emse.fr/kg/emse/fayol/";


    public static void main(String[] args) throws Exception {
        //download and read calendar file or read if necessary
        String action = getCommand();

        if (action.equals(DOWNLOAD_COMMAND) || action.equals(READ_COMMAND)) {
            if (action.equals(DOWNLOAD_COMMAND)) {
                String url = getUrl();
                downloadICS(url);
            }


            FileInputStream fin = new FileInputStream(CALENDAR_FILE_NAME);
            CalendarBuilder builder = new CalendarBuilder();
            Calendar calendar = builder.build(fin);
            parseCalendarToRDF(calendar);
        }

        if (action.equals(ADD_ATTENDEE_COMMAND)){
            List<String> attendeeDetails =  getAttendeeDetails();

            addAttendeeToEvent(attendeeDetails.get(0),attendeeDetails.get(1));
        }


    }

    public static String getCommand() throws IOException {
        // Enter data using BufferReader
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in));

        // Reading data using readLine
        System.out.println("Please enter a run command: download | read | add_attendee:");
        String command = reader.readLine().toUpperCase();

        while (!command.equals(DOWNLOAD_COMMAND) && !command.equals(READ_COMMAND) && !command.equals(ADD_ATTENDEE_COMMAND)) {
            System.out.println("Command must be either of DOWNLOAD | READ | ADD_ATTENDEE");
            command = reader.readLine().toUpperCase();
        }

        return command;
    }

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

        System.out.println("Please enter a attendee uri: eg http://example.com/mezIgnas");
        String attendeeUri = reader.readLine();

        while (attendeeUri.isEmpty()) {
            System.out.println("event url is invalid");
            attendeeUri = reader.readLine();
        }

        List <String> attendeeDetails = new ArrayList<String>();
        attendeeDetails.add(attendeeUri);
        attendeeDetails.add(eventUri);

        return attendeeDetails;
    }

    public static String getUrl() throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in));

        // Reading data using readLine
        System.out.println("Please enter a url to file:");

        String url = reader.readLine();

        while (url.isEmpty()) {
            System.out.println("Please enter a valid url");
            url = reader.readLine();
        }

        return url;
    }

    public static void parseCalendarToRDF(Calendar calendar) throws Exception {
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
//            model.write(System.out, "Turtle");

        model.write(bufferedWriter, "Turtle");
        bufferedWriter.close();
        uploadTurtleFile(LDP_DESTINATION, true, 0);


        for (CalendarComponent calendarEvent : calendarList) {
            model = ModelFactory.createDefaultModel();
//            eventsInfo = model.createResource("https://mines-saint-etienne.cps2.com/mycalendar");

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

//            eventInfo = model.createResource(EVENTS_PREFIX+"#"+ eventName.substring(0, 1).toLowerCase() + eventName.substring(1) + "-"+ eventCount.toString());
            eventInfo = model.createResource(eventName.substring(0, 1).toLowerCase() + eventName.substring(1) + "-" + eventCount.toString());


//            eventInfo.addProperty(A_THING, SCHEMA_ORG_PREFIX + "Event");
            eventInfo.addProperty(RDF.type, SCHEMA_ORG_PREFIX + "Event");

            for (Property eventDetail : eventDetails) {
                String detailName = eventDetail.getName();


                switch (detailName) {
                    case _DTSTAMP -> eventInfo.addProperty(DATE_TIME, model.createTypedLiteral(createDateTimeObject(eventDetail.getValue())));
                    case _DTSTART -> eventInfo.addProperty(DATE_START,  model.createTypedLiteral(createDateTimeObject(eventDetail.getValue())));
                    case _DTEND -> eventInfo.addProperty(DATE_END, model.createTypedLiteral(createDateTimeObject(eventDetail.getValue())));
                    case _SUMMARY -> eventInfo.addProperty(SUMMARY, model.createTypedLiteral(eventDetail.getValue()));
                    case _LOCATION -> {
                        eventInfo.addProperty(LOCATION, model.createTypedLiteral( convertLocationToTerritoireIRI(eventDetail.getValue(), EMSE_TERRITOIRE_PREFIX)));
                    }
                    case _DESCRIPTION -> eventInfo.addProperty(DESCRIPTION, model.createTypedLiteral(eventDetail.getValue()));
                    case _UID -> eventInfo.addProperty(IDENTIFIER, model.createTypedLiteral(eventDetail.getValue()));
                    case _CREATED -> eventInfo.addProperty(DATE_CREATED, model.createTypedLiteral(createDateTimeObject(eventDetail.getValue())));
                    case _LAST_MODIFIED -> eventInfo.addProperty(DATE_MODIFIED, model.createTypedLiteral(createDateTimeObject( eventDetail.getValue())));
                    case _SEQUENCE -> eventInfo.addProperty(SEQUENCE,model.createTypedLiteral(Integer.parseInt(eventDetail.getValue())));
                }
            }

            model.createStatement(eventInfo, RDF.type, eventsInfo);

            tempFileName = CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME + "-" + eventCount.toString() + ".ttl";
//            eventFileName.add(tempFileName);

            eventCount++;

            writer = new FileWriter(tempFileName);
            bufferedWriter = new BufferedWriter(writer);
//            model.write(System.out, "Turtle");

            model.write(bufferedWriter, "Turtle");
            bufferedWriter.close();
        }

//        mergeFiles(eventFileName, CALENDAR_OUTPUT_TURTLE_FILE_NAME);

        int cc = 1;
        while (cc < eventCount) {
            uploadTurtleFile(LDP_DESTINATION, false, cc);
            cc++;

        }

//        System.out.println("Output file: " + CALENDAR_OUTPUT_TURTLE_FILE_NAME + " generated and stores in project root folder");
        System.out.println("Generated output has been uploaded to defined DB: Fuseki or LDP");
        System.out.println("::::::::::::::::::::");
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
            Files.deleteIfExists(Paths.get(fileName));
        }

        pw.flush();
        pw.close();

    }

    public static void uploadTurtleFile(String destination, Boolean isContainer, Integer count) throws Exception {
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

//                String requestBody = Files.readString(Path.of(CALENDAR_OUTPUT_TURTLE_FILE_NAME), StandardCharsets.UTF_8);
                String requestBody;
                if (isContainer) {
                    requestBody = Files.readString(Path.of(CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME + "_container.ttl"), StandardCharsets.UTF_8);

                } else {
                    //container child
                    requestBody = Files.readString(Path.of(CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME + "-" + count.toString() + ".ttl"), StandardCharsets.UTF_8);

                }
                System.out.println(requestBody);
                StringEntity requestBodyEntity = new StringEntity(requestBody);
                post.setEntity(requestBodyEntity);

//            try (CloseableHttpClient httpClient = HttpClients.createDefault();
//                 CloseableHttpResponse response = httpClient.execute(post)) {
                CloseableHttpClient httpClient = HttpClients.createDefault();
                CloseableHttpResponse response = httpClient.execute(post);

                //DELETE TEMP FILE HERE
                Files.deleteIfExists(Paths.get(isContainer ? CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME + "_container.ttl" : CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME + "-" + count.toString() + ".ttl"));

                System.out.println(EntityUtils.toString(response.getEntity()));
            } catch (Exception e) {
                System.out.println(e.getMessage());
                throw new Exception(e);
            }
        }
    }

    public static void deleteRemoteResource(String url) throws Exception {
        //deleted from
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

    public static XSDDateTime createDateTimeObject(String dateTimeString) throws ParseException {
        String year = dateTimeString.substring(0,4);
        String month = dateTimeString.substring(4,6);
        String day = dateTimeString.substring(6,8);
        String hour = dateTimeString.substring(9,11);
        String minute = dateTimeString.substring(11,13);
        String second = dateTimeString.substring(13,15);

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy;HH:mm:ss");

        Date date = simpleDateFormat.parse(day+"-"+month+"-"+year+";"+hour+":"+minute+":"+second);
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTime(date);
        return new XSDDateTime(calendar);
    }

    public static void addAttendeeToEvent(String attendeeURI,String eventUrl) throws Exception {
        System.out.println("event url: "+ eventUrl);
        //fetch event
//        HttpGet get = new HttpGet(TERRITOIRE_CONTAINER_SERVICE_URL+eventName+"/");
        HttpGet get = new HttpGet(eventUrl);
        get.addHeader("Authorization", AUTH_TOKEN);

        CloseableHttpResponse response;
        String eTagHeader;
        try{
            CloseableHttpClient httpClient = HttpClients.createDefault();
            response = httpClient.execute(get);

            eTagHeader = response.getFirstHeader("Etag").getValue();

        }catch (Exception e) {
            System.out.println(e.getMessage());
            throw new Exception(e);
        }

//        System.out.println(EntityUtils.toString(response.getEntity()));
        String data = EntityUtils.toString(response.getEntity());

        // write results to file
        String tempFileName = FETCHED_RESOURCE_TEMP_NAME; //REFACTOR TO include random numbers

        FileWriter writer = new FileWriter(tempFileName);
        BufferedWriter bufferedWriter = new BufferedWriter(writer);

        bufferedWriter.write(data);
        bufferedWriter.close();

        //read file into jena and update or delete and repost
        Model model = ModelFactory.createDefaultModel() ;
        model.read(FETCHED_RESOURCE_TEMP_NAME) ;

        final org.apache.jena.rdf.model.Property ATTENDEE = model.createProperty(SCHEMA_ORG_PREFIX + "attendee");
        model.getResource(eventUrl).addProperty(ATTENDEE,model.createResource(attendeeURI));

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
//            model.write(System.out, "Turtle");

        model.write(bufferedWriter, "Turtle");

        //push file online

        // ALTERNATIVE, DELETE OLD RESOURCE AND POST UPDATE
        try {

            HttpPut put = new HttpPut(eventUrl);
            put.addHeader("Authorization", AUTH_TOKEN);
            put.addHeader("Accept", "text/turtle");
            put.addHeader("Content-Type", "text/turtle");
            put.addHeader("If-Match", eTagHeader);
            put.addHeader("Prefer", "http://www.w3.org/ns/ldp#RDFSource; rel=interaction-model");


            String requestBody = Files.readString(Path.of(FETCHED_RESOURCE_TEMP_NAME));
//            String requestBody = attendeeURI;
//            System.out.println(requestBody);

            StringEntity requestBodyEntity = new StringEntity(requestBody);
            put.setEntity(requestBodyEntity);

//            try (CloseableHttpClient httpClient = HttpClients.createDefault();
//                 CloseableHttpResponse response = httpClient.execute(post)) {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            response = httpClient.execute(put);

//            System.out.println(EntityUtils.toString(response.getEntity()));

            Files.deleteIfExists(Paths.get(FETCHED_RESOURCE_TEMP_NAME));

            System.out.println(response.toString());
            System.out.println("Attendee added to event:::::::::");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new Exception(e);
        }
    }

}