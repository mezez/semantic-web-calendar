package org.semanticwebproject;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.CalendarComponent;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

import java.io.*;
import java.util.List;

import static org.semanticwebproject.lib.Constants.*;
import static org.semanticwebproject.lib.Helpers.convertLocationToTerritoireIRI;
import static org.semanticwebproject.lib.Helpers.downloadICS;

public class Main {

    //PREFIXES
    public static final String SCHEMA_ORG_PREFIX = "https://schema.org/";
    public static final String EXAMPLE_PREFIX = "http://example.org/";
    public static final String EMSE_TERRITOIRE_PREFIX = "https://territoire.emse.fr/kg/emse/fayol/";


    public static void main(String[] args) throws IOException, ParserException {
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

    public static void parseCalendarToRDF(Calendar calendar) {
        List<CalendarComponent> calendarList = calendar.getComponentList().getAll();

        for (CalendarComponent calendarEvent : calendarList) {
            Model model = ModelFactory.createDefaultModel();
            Resource eventsInfo = model.createResource("https://mines-saint-etienne.cps2.com/calendar");

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

            String eventName = calendarEvent.getName();
            List<Property> eventDetails = calendarEvent.getProperties();

            //loop through event details and form RDF

            Resource eventInfo = model.createResource(eventName.substring(0, 1).toLowerCase() + eventName.substring(1));

            eventInfo.addProperty(A_THING, SCHEMA_ORG_PREFIX + "Event");

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
            model.write(System.out, "Turtle");
        }


    }
}