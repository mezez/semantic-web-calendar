package org.semanticwebproject.lib;

import org.apache.jena.rdf.model.Model;

import java.io.*;
import java.net.URL;
import java.util.Arrays;

import static org.semanticwebproject.lib.Constants.CALENDAR_FILE_NAME;
import static org.semanticwebproject.lib.Constants.FETCHED_RESOURCE_TEMP_NAME;

public class Helpers {


    public static void downloadICS(String urlString) throws IOException {

        try {
            URL url = new URL(urlString);
            BufferedInputStream bis = new BufferedInputStream(url.openStream());

            FileOutputStream fis = new FileOutputStream(CALENDAR_FILE_NAME);
            byte[] buffer = new byte[1024];
            int count = 0;
            while ((count = bis.read(buffer, 0, 1024)) != -1) {
                fis.write(buffer, 0, count);
            }
            fis.close();
            bis.close();

            System.out.println("File downloaded to root of project folder");
            System.out.println("File name: " + CALENDAR_FILE_NAME);
        } catch (Exception exception) {
            System.out.println("An error occurred while downloading file");
        }
    }

    public static String convertLocationToTerritoireIRI(String location, String prefix) {
        String[] splitLocation = location.split("\\s+");
        if (Arrays.stream(splitLocation).anyMatch(str -> str.toLowerCase().equals("emse")) && Arrays.stream(splitLocation).anyMatch(str -> str.toLowerCase().equals("fauriel"))) {
            //convert to Territoire IRI
            String roomName = splitLocation[splitLocation.length - 1];

            switch (roomName.toLowerCase().substring(0, 2)) {
                case "s1" -> location = prefix + "1ET/" + roomName.substring(1).replace(".","");
                case "s2" -> location = prefix + "2ET/" + roomName.substring(1).replace(".","");
                case "s3" -> location = prefix + "3ET/" + roomName.substring(1).replace(".","");
                case "s4" -> location = prefix + "4ET/" + roomName.substring(1).replace(".","");
                case "s5" -> location = prefix + "5ET/" + roomName.substring(1).replace(".","");
                case "s6" -> location = prefix + "6ET/" + roomName.substring(1).replace(".","");
                default -> {
                }
            }



        }
        return location;
    }

    public static void writeStringToFile(String content, String fileName) throws IOException {
        FileWriter fileWriter = new FileWriter(fileName);
        BufferedWriter writer = new BufferedWriter(fileWriter);
        writer.write(content);

        writer.close();
        fileWriter.close();
    }

    public static void writeModelToFIle(String fileName, Model model) throws IOException {
        //write to file
        FileWriter writer = new FileWriter(fileName);
        BufferedWriter bufferedWriter = new BufferedWriter(writer);
//            model.write(System.out, "Turtle");

        model.write(bufferedWriter, "Turtle");
        bufferedWriter.close();
        writer.close();
    }
}
