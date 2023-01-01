package org.semanticwebproject.lib;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.topbraid.jenax.util.JenaUtil;
import org.topbraid.shacl.validation.ValidationUtil;
import org.topbraid.shacl.vocabulary.SH;

import static org.semanticwebproject.lib.Constants.*;

public class Helpers {

    private static Logger logger = LoggerFactory.getLogger(Helpers.class);
    // Why This Failure marker
    private static final Marker WTF_MARKER = MarkerFactory.getMarker("WTF");

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
                case "s1" -> location = prefix + "1ET/" + roomName.substring(1).replace(".", "");
                case "s2" -> location = prefix + "2ET/" + roomName.substring(1).replace(".", "");
                case "s3" -> location = prefix + "3ET/" + roomName.substring(1).replace(".", "");
                case "s4" -> location = prefix + "4ET/" + roomName.substring(1).replace(".", "");
                case "s5" -> location = prefix + "5ET/" + roomName.substring(1).replace(".", "");
                case "s6" -> location = prefix + "6ET/" + roomName.substring(1).replace(".", "");
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

    public static boolean validateWithSHACL(String fileContent, Boolean isCPS2Event) {
        boolean conforms = false;
        try {
            String shape;

            if (isCPS2Event) {
                shape = Files.readString(Path.of(SHACL_VALIDATION_SHAPE_CPS2_EVENT), StandardCharsets.UTF_8);

            } else {
                shape = Files.readString(Path.of(SHACL_VALIDATION_SHAPE), StandardCharsets.UTF_8);
            }


            Model dataModel = JenaUtil.createDefaultModel();
            dataModel.read(fileContent);
            Model shapeModel = JenaUtil.createDefaultModel();
            shapeModel.read(shape);

            Resource reportResource = ValidationUtil.validateModel(dataModel, shapeModel, true);
            conforms = reportResource.getProperty(SH.conforms).getBoolean();
            logger.trace("Conforms = " + conforms);

            if (!conforms) {
//                String report = path.toFile().getAbsolutePath() + SHACL_VALIDATION_REPORTS;
                File reportFile = new File(SHACL_VALIDATION_REPORTS);
                reportFile.createNewFile();
                OutputStream reportOutputStream = new FileOutputStream(reportFile);

                RDFDataMgr.write(reportOutputStream, reportResource.getModel(), RDFFormat.TTL);
            }

        } catch (Throwable t) {
            logger.error(WTF_MARKER, t.getMessage(), t);
        }
        return conforms;
    }
}
