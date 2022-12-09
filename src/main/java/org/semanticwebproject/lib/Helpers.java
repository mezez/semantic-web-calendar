package org.semanticwebproject.lib;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

import static org.semanticwebproject.lib.Constants.CALENDAR_FILE_NAME;

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
            System.out.println("File name: "+ CALENDAR_FILE_NAME);
        }catch (Exception exception){
            System.out.println("An error occurred while downloading file");
        }
    }
}
