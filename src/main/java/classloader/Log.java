package classloader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class Log {
    private static FileWriter writer;

    private static Path uptimeFile = new File("/proc/uptime").toPath();
    public static String uptime() {
        try {
            return new String(Files.readAllBytes(uptimeFile)).split(" ")[0];
        } catch (Exception e) {
            return "-1";
        }
    }

    public static String throwToString(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    public static void log(Object msg) {
        try {
            if (writer == null) {
                writer = new FileWriter("/var/log/patcher.log", true);
            }

            writer.write('[');
            writer.write(uptime());
            writer.write("] ");

            if (msg instanceof Throwable) {
                writer.write(throwToString((Throwable) msg));
            } else {
                writer.write(msg.toString());
            }
            writer.write('\n');
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
