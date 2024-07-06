package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogParser implements ILogParser {
    Pattern pattern = Pattern.compile(
            "^\\[([A-Z]+)\\s*([A-Za-z0-9\\s_\\-!@#$%^&*()+|<?.:=\\[\\],]+?),(\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d+)\\]:(.*)$");
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS[SSS[SSS]]]");

    @Override
    public void load(Log log) throws InterruptedException {
        log.loadStatus.isLoading = true;

        LogDetail detail = null;
        int id = 0;
        for (Path p : log.loadStatus.allPaths) {
            String currentFileName = p.getFileName().toString();
            try (var reader = Files.newBufferedReader(p)) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.matches()) {
                        if (detail != null) {
                            log.lines.add(detail);
                        }
                        String dt = LocalDateTime.now().getYear() + "-" + matcher.group(3);
                        ZonedDateTime zonedDateTime = LocalDateTime.parse(dt, dateTimeFormatter)
                                .atZone(ZoneId.of("Asia/Hong_Kong"));
                        detail = new LogDetail(id++, zonedDateTime, matcher.group(2), matcher.group(1), p.toString(),
                                matcher.group(4));
                        log.loadStatus.currentFileName = currentFileName;
                    } else {
                        if (detail != null) {
                            detail.appendLine(line);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            log.loadStatus.loadedPaths.add(p);
        }
        if (detail != null) {
            log.lines.add(detail);
        }

        log.loadStatus.isLoading = false;
    }
}