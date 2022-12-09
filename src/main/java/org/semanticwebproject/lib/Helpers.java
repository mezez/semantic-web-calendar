package org.semanticwebproject.lib;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

public class Helpers {

    public static String calendarName = "calendar.ics";

    public static void downloadICS(String urlString) throws IOException {
        URL url = new URL(urlString);
        BufferedInputStream bis = new BufferedInputStream(url.openStream());

        FileOutputStream fis = new FileOutputStream(calendarName);
        byte[] buffer = new byte[1024];
        int count=0;
        while((count = bis.read(buffer,0,1024)) != -1)
        {
            fis.write(buffer, 0, count);
        }
        fis.close();
        bis.close();
    }
}
