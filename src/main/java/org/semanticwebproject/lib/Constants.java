package org.semanticwebproject.lib;

public class Constants {
    //UPLOADS
    public static final String LDP_DESTINATION = "remote";
    public static final String FUSEKI_DESTINATION = "fuseki";

    //SERVICES
    public static final String LOCAL_FUSEKI_SERVICE_URL = "http://localhost:3030/semweb/";
    public static final String TERRITOIRE_SERVICE_URL = "https://territoire.emse.fr/ldp/";
    public static final String CONTAINER_NAME_URL = "mieventcontainer/";
    public static final String TERRITOIRE_CONTAINER_SERVICE_URL = "https://territoire.emse.fr/ldp/"+CONTAINER_NAME_URL;
    public static final String CONTAINER_NAME = "mez-ignas";
    public static final String AUTH_TOKEN = "Basic bGRwdXNlcjpMaW5rZWREYXRhSXNHcmVhdA==";

    //COMMANDS
    public static final String DOWNLOAD_COMMAND = "DOWNLOAD";
    public static final String READ_COMMAND = "READ";
    public static final String ADD_ATTENDEE_COMMAND = "ADD_ATTENDEE";

    //FILES
    public static String CALENDAR_FILE_NAME = "calendar.ics";
    public static String CALENDAR_OUTPUT_TURTLE_FILE_NAME = "calender_output.ttl";
    public static String CALENDAR_OUTPUT_TURTLE_FILE_TEMP_NAME = "calender_output_temp";
    public static String FETCHED_RESOURCE_TEMP_NAME = "fetched_resource_temp.ttl";


    //CALENDAR FORMATS
    public static String BEGIN = "BEGIN";
    public static String END = "END";
    public static String VEVENT = "VEVENT";
    public static final String _DTSTAMP = "DTSTAMP";
    public static final String _DTSTART = "DTSTART";
    public static final String _DTEND = "DTEND";
    public static final String _SUMMARY = "SUMMARY";
    public static final String _LOCATION = "LOCATION";
    public static final String _DESCRIPTION = "DESCRIPTION";
    public static final String _UID = "UID";
    public static final String _CREATED = "CREATED";
    public static final String _LAST_MODIFIED = "LAST-MODIFIED";
    public static final String _SEQUENCE = "SEQUENCE";
}
