package org.semanticwebproject;

import org.semanticwebproject.lib.CalendarEvent;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static org.semanticwebproject.lib.Constants.*;
import static org.semanticwebproject.lib.Helpers.downloadICS;

public class Main {
    public static void main(String[] args) throws IOException {
        //download and read calendar file or read if necessary
        String action = getCommand();

        if (action.equals(DOWNLOAD_COMMAND)){
            String url = getUrl();
            downloadICS(url);
        }

        //READ file
        BufferedReader reader = new BufferedReader(new FileReader(CALENDAR_FILE_NAME));

        String line;


        CalendarEvent event = null;
        List<CalendarEvent> events = new ArrayList<CalendarEvent>();
        while ((line = reader.readLine()) != null) {
            String[] columns = line.split(":",2);

            if (columns[0].equals(BEGIN) && columns[1].equals(VEVENT)){
                // build object from next line
                event = new CalendarEvent();
                continue;
            }
            if (columns[0].equals(END) && columns[1].equals(VEVENT)){
                // build object from next line
                events.add(event);
                continue;
            }
            assert event != null;
            System.out.println(columns[0]);
            System.out.println(columns[1]);

            if(columns[0].equals(DTSTAMP)){
                event.setStampDate(columns[1]);
            }
            if(columns[0].equals(DTSTART)){
                event.setStartDate(columns[1]);
            }
            if(columns[0].equals(DTEND)){
                event.setEndDate(columns[1]);
            }
            if(columns[0].equals(SUMMARY)){
                event.setSummary(columns[1]);
            }
            if(columns[0].equals(LOCATION)){
                event.setLocation(columns[1]);
            }
            if(columns[0].equals(DESCRIPTION)){
                event.setDescription(columns[1]);
            }
            if(columns[0].equals(UID)){
                event.setUID(columns[1]);
            }
            if(columns[0].equals(CREATED)){
                event.setCreatedAt(columns[1]);
            }
            if(columns[0].equals(LAST_MODIFIED)){
                event.setLastModified(columns[1]);
            }
            if(columns[0].equals(SEQUENCE)){
                event.setSequence(columns[1]);
            }
        }

        System.out.println(events);

    }

    public static String getCommand() throws IOException {
        // Enter data using BufferReader
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in));

        // Reading data using readLine
        System.out.println("Please enter a run command: download or read:");
        String command = reader.readLine().toUpperCase();

        while (!command.equals(DOWNLOAD_COMMAND) && !command.equals(READ_COMMAND)){
            System.out.println("Command must be either DOWNLOAD or READ");
            command = reader.readLine().toUpperCase();
        }

        return command;
    }

    public static String getUrl() throws IOException{
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in));

        // Reading data using readLine
        System.out.println("Please enter a url to file:");

        String url = reader.readLine();

        while (url.isEmpty()){
            System.out.println("Please enter a valid url");
            url = reader.readLine();
        }

        return url;
    }
}