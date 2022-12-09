package org.semanticwebproject;

import org.semanticwebproject.lib.CalendarEvent;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import static org.semanticwebproject.lib.Constants.*;
import static org.semanticwebproject.lib.Helpers.downloadICS;

public class Main {
    public static void main(String[] args) throws IOException {
        //download and read calendar file or read if necessary
        String action = READ_COMMAND;
        try {
            action = args[0];
        }catch (Exception exception){
            System.out.println("An action argument is needed to run application");
            System.exit(0);
        }

        if (action.toString().equals(DOWNLOAD_COMMAND)){
            String url = args[1].toString();
            downloadICS(url);
        }

        //READ file
        BufferedReader reader = new BufferedReader(new FileReader(CALENDAR_FILE_NAME));

        String line;
        boolean firstEvent = true;
        boolean startBuilding = false;


        boolean firstObject = true;

        CalendarEvent event = null;
        while ((line = reader.readLine()) != null) {
            String[] columns = line.split(":");

            if (columns[0].equals(BEGIN) && columns[1].equals(VEVENT)){
                // build object from next line
                event = new CalendarEvent();
                continue;
            }
            if (columns[0].equals(END) && columns[1].equals(VEVENT)){
                // build object from next line
                continue;
            }

            assert event != null;
            event.setStampDate(columns[1]);
            //etc
        }

    }
}