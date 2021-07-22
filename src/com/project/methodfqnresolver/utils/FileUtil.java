package com.project.methodfqnresolver.utils;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;

public class FileUtil {
    public static Integer getLinesOfFile(String pathFile) {
        int lineCounts = 0;
        LineNumberReader reader = null;
        try {
            reader = new LineNumberReader(new FileReader(pathFile));
            while (reader.readLine() != null);
            lineCounts = reader.getLineNumber();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        }
        return lineCounts;
    }

    public static String getFileName(String pathFile) {
        String[] pathFileSlitted = pathFile.split("/");
        String fileName = pathFileSlitted[pathFileSlitted.length-1];
        return fileName;
    }
}
