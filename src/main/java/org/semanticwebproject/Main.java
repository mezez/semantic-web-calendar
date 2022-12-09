package org.semanticwebproject;

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
        int currentLine = 1;
        int startLine = 0;
        while ((line = reader.readLine()) != null) {
            String[] columns = line.split(":");

            if (columns[0].equals(BEGIN) && columns[1].equals(VEVENT)){
                // build object from next line
                startLine = currentLine + 1;
            }

            if (startLine > 0 ){
                //buildg
            }
            currentLine++;
        }

    }
}