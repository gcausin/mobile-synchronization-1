package msync.codegeneration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;

import org.springframework.core.io.DefaultResourceLoader;

public class Utils {

    public static String readTemplate(String pathToResource, String sqlSection) {
        try {
            InputStream inputStream = new DefaultResourceLoader(Utils.class.getClassLoader()).getResource(pathToResource).getInputStream();
            final Reader r = new BufferedReader(new InputStreamReader(inputStream));
            final LineNumberReader reader = new LineNumberReader(r);
            String line = null;

            while ((line = reader.readLine()) != null) {
                if (line.equals(sqlSection)) {
                    break;
                }
            }

            String sql = new String();

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("    ")) {
                    sql += line.substring(4) + "\n";
                }
                else {
                    break;
                }
            }

            return sql;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String readTextFileFromClasspath(String file) throws IOException {
        try (
            Scanner srcStream = new Scanner(
                                        new DefaultResourceLoader(Utils.class.getClassLoader())
                                                .getResource(file).getInputStream(), "UTF-8")) {
            return srcStream.useDelimiter("\\A").next();
        }
        catch(FileNotFoundException fileNotFoundException) {
        	return null;
        }
    }

    public static String readTextFileFromFileSystem(String fileName) throws IOException {
        return new String(Files.readAllBytes(Paths.get(fileName)));
    }

    public static String firstLetterToLowerCase(String string) {
        return firstLetterToLowerCase(new StringBuffer(string));
    }

    private static String firstLetterToLowerCase(StringBuffer stringBuffer) {
        return stringBuffer.replace(0, 1, stringBuffer.substring(0, 1).toLowerCase()).toString();
    }

    public static String firstLetterToUpperCase(String string) {
        return firstLetterToUpperCase(new StringBuffer(string));
    }

    private static String firstLetterToUpperCase(StringBuffer stringBuffer) {
        return stringBuffer.replace(0, 1, stringBuffer.substring(0, 1).toUpperCase()).toString();
    }

    public static String pluralOf(String propertyName) {
    	if (Arrays.asList("s", "x", "z", "ch", "sh").stream().anyMatch(e -> propertyName.endsWith(e))) {
    		return propertyName + "es";
    	}
    	
        if (propertyName.endsWith("y") && !isVowel(propertyName.charAt(propertyName.length() - 2)))
            return propertyName.substring(0, propertyName.length() - 1) + "ies";

        return propertyName + "s";
    }

    /**
     * Returns true if ch is a vowel.
     */
    public static boolean isVowel(final char ch) {
    	return Arrays.asList('a', 'e', 'i', 'o', 'u').stream().anyMatch(c -> c == ch);
    }

    public static boolean deleteDir(File dir) {

        if (dir.isDirectory()) {
            String[] children = dir.list();

            for (int i = 0; i < children.length; i++) {
                if (!deleteDir(new File(dir, children[i]))) {
                    return false;
                }
            }
        }

        return dir.delete();
    }

    public static String readTemplate(String key) {
        return readTemplate("templates/code", key);
    }

    public static String readDatabaseTemplate(String database, String key) {
        return readTemplate("templates/" + database, key);
    }

    public static <T> T coalesce(T... allArgs) {
        return Arrays.asList(allArgs)
            .stream()
            .filter(a -> a != null)
            .findFirst()
            .orElse(null);
    }

}
