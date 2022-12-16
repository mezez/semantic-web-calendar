package org.semanticwebproject;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.CalendarComponent;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.vocabulary.RDF;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
    public static final String EVENTS_PREFIX = "eventContainer/";


    public static void main(String[] args) throws Exception {
        //download and read calendar file or read if necessary
        String action = getCommand();

        if (action.equals(DOWNLOAD_COMMAND)) {
            String url = getUrl();
            downloadICS(url);
        }

        FileInputStream fin = new FileInputStream(CALENDAR_FILE_NAME);
        CalendarBuilder builder = new CalendarBuilder();
        Calendar calendar = builder.build(fin);
        parseCalendarToRDF(calendar);


    }

    public static String getCommand() throws IOException {
        // Enter data using BufferReader
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in));

        // Reading data using readLine
        System.out.println("Please enter a run command: download or read:");
        String command = reader.readLine().toUpperCase();

        while (!command.equals(DOWNLOAD_COMMAND) && !command.equals(READ_COMMAND)) {
            System.out.println("Command must be either DOWNLOAD or READ");
            command = reader.readLine().toUpperCase();
        }

        return command;
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
        String eventName = EVENTS_PREFIX;

        Resource eventInfo = model.createResource(eventName);
        eventInfo.addProperty(RDF.type, "Group of Events");
        eventInfo.addProperty(RDF.type, W3_LDP_PREFIX + "BasicContainer");

        model.createStatement(eventInfo, RDF.type, eventsInfo);


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

            eventInfo = model.createResource(EVENTS_PREFIX+"#"+ eventName.substring(0, 1).toLowerCase() + eventName.substring(1) + "-"+ eventCount.toString());


//            eventInfo.addProperty(A_THING, SCHEMA_ORG_PREFIX + "Event");
            eventInfo.addProperty(RDF.type, SCHEMA_ORG_PREFIX + "Event");

            for (Property eventDetail : eventDetails) {
                String detailName = eventDetail.getName();


                switch (detailName) {
                    case _DTSTAMP -> eventInfo.addProperty(DATE_TIME, eventDetail.getValue());
                    case _DTSTART -> eventInfo.addProperty(DATE_START, eventDetail.getValue());
                    case _DTEND -> eventInfo.addProperty(DATE_END, eventDetail.getValue());
                    case _SUMMARY -> eventInfo.addProperty(SUMMARY, eventDetail.getValue());
                    case _LOCATION -> {
                        eventInfo.addProperty(LOCATION, convertLocationToTerritoireIRI(eventDetail.getValue(), EMSE_TERRITOIRE_PREFIX));
                    }
                    case _DESCRIPTION -> eventInfo.addProperty(DESCRIPTION, eventDetail.getValue());
                    case _UID -> eventInfo.addProperty(IDENTIFIER, eventDetail.getValue());
                    case _CREATED -> eventInfo.addProperty(DATE_CREATED, eventDetail.getValue());
                    case _LAST_MODIFIED -> eventInfo.addProperty(DATE_MODIFIED, eventDetail.getValue());
                    case _SEQUENCE -> eventInfo.addProperty(SEQUENCE, eventDetail.getValue());
                }
            }

            model.createStatement(eventInfo, RDF.type, eventsInfo);

            String tempFileName = CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME + "-" + eventCount.toString() + ".ttl";
            eventFileName.add(tempFileName);

            eventCount++;

            FileWriter writer = new FileWriter(tempFileName);
            BufferedWriter bufferedWriter = new BufferedWriter(writer);
//            model.write(System.out, "Turtle");

            model.write(bufferedWriter, "Turtle");
            bufferedWriter.close();
        }

        mergeFiles(eventFileName, CALENDAR_OUTPUT_TURTLE_FILE_NAME);

        uploadTurtleFile(LDP_DESTINATION);

        System.out.println("Output file: " + CALENDAR_OUTPUT_TURTLE_FILE_NAME + " generated and stores in project root folder");
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

    public static void uploadTurtleFile(String destination) throws Exception {
        if (destination.equals(FUSEKI_DESTINATION)) {
            try (RDFConnection conn = RDFConnectionFactory.connect(LOCAL_FUSEKI_SERVICE_URL)) {
                conn.put(CALENDAR_OUTPUT_TURTLE_FILE_NAME);
            }
        } else {
            //upload to territoire

            try {
                HttpPost post = new HttpPost(TERRITOIRE_CONTAINER_SERVICE_URL);
                post.addHeader("Authorization", AUTH_TOKEN);
                post.addHeader("Accept", "text/turtle");
                post.addHeader("Content-Type", "text/turtle");
                post.addHeader("Link", "<http://www.w3.org/ns/ldp#BasicContainer>; rel=\"type\"");
                post.addHeader("Prefer", "http://www.w3.org/ns/ldp#Container; rel=interaction-model");
//            post.addHeader("Slug", CONTAINER_NAME);
                post.addHeader("Slug", "testtest2");

                String requestBody = Files.readString(Path.of(CALENDAR_OUTPUT_TURTLE_FILE_NAME), StandardCharsets.UTF_8);
                System.out.println(requestBody);
                StringEntity requestBodyEntity = new StringEntity(requestBody);
                post.setEntity(requestBodyEntity);

//            try (CloseableHttpClient httpClient = HttpClients.createDefault();
//                 CloseableHttpResponse response = httpClient.execute(post)) {
                CloseableHttpClient httpClient = HttpClients.createDefault();
                CloseableHttpResponse response = httpClient.execute(post);

                System.out.println(EntityUtils.toString(response.getEntity()));
            } catch (Exception e) {
                System.out.println(e.getMessage());
                throw new Exception(e);
            }
        }
    }

    public static void uploadTurtleFile2(String destination, Integer count) throws Exception {
        if (destination.equals(FUSEKI_DESTINATION)) {
            try (RDFConnection conn = RDFConnectionFactory.connect(LOCAL_FUSEKI_SERVICE_URL)) {
                conn.put(CALENDAR_OUTPUT_TURTLE_FILE_NAME);
            }
        } else {
            //upload to territoire

            try {
                HttpPost post = new HttpPost(TERRITOIRE_CONTAINER_SERVICE_URL);
                post.addHeader("Authorization", AUTH_TOKEN);
                post.addHeader("Accept", "text/turtle");
                post.addHeader("Content-Type", "text/turtle");
                post.addHeader("Link", "<http://www.w3.org/ns/ldp#BasicContainer>; rel=\"type\"");
                post.addHeader("Prefer", "http://www.w3.org/ns/ldp#Container; rel=interaction-model");
//            post.addHeader("Slug", CONTAINER_NAME);
                post.addHeader("Slug", "testtest2");

                String requestBody = Files.readString(Path.of(CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME+ "-" + count.toString() + ".ttl"), StandardCharsets.UTF_8);
                System.out.println(requestBody);
                StringEntity requestBodyEntity = new StringEntity(requestBody);
                post.setEntity(requestBodyEntity);

//            try (CloseableHttpClient httpClient = HttpClients.createDefault();
//                 CloseableHttpResponse response = httpClient.execute(post)) {
                CloseableHttpClient httpClient = HttpClients.createDefault();
                CloseableHttpResponse response = httpClient.execute(post);

                System.out.println(EntityUtils.toString(response.getEntity()));
            } catch (Exception e) {
                System.out.println(e.getMessage());
                throw new Exception(e);
            }
        }
    }

}