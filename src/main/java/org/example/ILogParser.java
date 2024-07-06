package org.example;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public interface ILogParser {

    class LogDetail {
        final int id;
        final ZonedDateTime time;
        final String threadName, priority, fileName;
        private String content;
        private StringBuilder contentBuilder;

        public LogDetail(int id, ZonedDateTime time, String threadName, String priority, String fileName,
                String content) {
            this.id = id;
            this.time = time;
            this.threadName = threadName;
            this.priority = priority;
            this.fileName = fileName;
            this.contentBuilder = new StringBuilder(content);
        }

        public String getContent() {
            if (contentBuilder == null) {
                return content;
            }

            content = contentBuilder.toString();
            contentBuilder = null;
            return content;
        }

        public void appendLine(String c) {
            contentBuilder.append("\n").append(c);
        }
    }

    class Log {
        List<LogDetail> lines = new ArrayList<>();
        LoadStatus loadStatus;

        public static Log load(List<Path> paths) {
            var l = new Log();
            l.loadStatus = new LoadStatus(new ArrayList<>(paths));
            return l;
        }

        public void start() throws InterruptedException {
            LogParser logParser = new LogParser();
            logParser.load(this);
        }
    }

    class LoadStatus {
        boolean isLoading = false;
        List<Path> loadedPaths = new ArrayList<>();
        List<Path> allPaths;
        String currentFileName = "";

        public LoadStatus(List<Path> paths) {
            allPaths = new ArrayList<>(paths);
        }
    }

    void load(Log log) throws InterruptedException;
}