package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogParser implements ILogParser {
    Pattern pattern = Pattern.compile(
            "^\\[([A-Z]+)\\s*([A-Za-z0-9\\s_\\-!@#$%^&*()+|<?.:=\\[\\],]+?),(\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d+)\\]:(.*)$");
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS[SSS[SSS]]]");

    @Override
    public void load(Log log) throws InterruptedException {
        log.loadStatus.isLoading = true;
        log.loadQueue.isLoading.set(true);

        LogDetail detail = null;
        int id = 0;
        int resultLimit = 1000;
        int resultCount = 0;
        for (Path p : log.loadStatus.allPaths) {
            if (resultCount > resultLimit) {
                break;
            }

            String currentFileName = p.getFileName().toString();

            try (var reader = Files.newBufferedReader(p)) {
                log.loadQueue.queue.add(new LoadProgress(p.getFileName().toString(), "start"));
                detail = null;
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.matches()) {
                        if (detail != null && isLineMatchFilter(detail, log.filter)) {
                            log.loadQueue.queue.add(detail);
                            resultCount += 1;
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
                log.loadQueue.queue.add(new LoadProgress(p.getFileName().toString(), "end"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            log.loadStatus.loadedPaths.add(p);
        }
        if (detail != null) {
            log.lines.add(detail);
        }

        log.loadStatus.isLoading = false;
        log.loadQueue.isLoading.set(false);
    }

    static boolean isLineMatchFilter(ILogParser.LogDetail detail, Map<String, Pattern> mapPattern) {
        if (mapPattern.isEmpty()) {
            return true;
        }
        Pattern patternDefault = mapPattern.get("*");
        boolean isAnyMatchTrue = mapPattern.size() == 1 && patternDefault != null;
        Map<String, String> map = new HashMap<>();
        map.put("C1", detail.priority);
        map.put("LEVEL", detail.priority);
        map.put("C2", detail.threadName);
        map.put("THREAD", detail.threadName);
        map.put("C3", detail.getContent());
        map.put("CONTENT", detail.getContent());

        for (Map.Entry<String, String> kv : map.entrySet()) {
            Pattern pattern = mapPattern.getOrDefault(kv.getKey(), patternDefault);
            if (pattern == null) {
                continue;
            }
            String content = kv.getValue();
            boolean isMatch = pattern.matcher(content).find();
            if (!isAnyMatchTrue) {
                if (!isMatch) {
                    return false;
                }
            } else {
                if (isMatch) {
                    return true;
                }
            }
        }
        return !isAnyMatchTrue;
    }
}