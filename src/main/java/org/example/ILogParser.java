package org.example;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.example.IResponse.IResponseBody;

public interface ILogParser {

    class LogDetail implements IResponseBody {
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

        public long getTimestamp() {
            return time.toInstant().toEpochMilli();
        }

        public String getThread() {
            return threadName;
        }

        public String getPriority() {
            return priority;
        }

        public String getFileName() {
            return fileName;
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

    record LoadProgress(String loadedPath, String progress) implements IResponseBody {
    }

    class Log {
        List<LogDetail> lines = new ArrayList<>();
        LoadStatus loadStatus;
        LoadQueue loadQueue;
        Map<String, Pattern> filter;

        public static Log load(List<Path> paths, Map<String, Pattern> filter) {
            var l = new Log();
            l.loadStatus = new LoadStatus(new ArrayList<>(paths));
            l.loadQueue = new LoadQueue();
            l.filter = filter == null ? Collections.emptyMap() : filter;
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

    class LoadQueue {
        AtomicBoolean isLoading = new AtomicBoolean(true);
        BlockingQueue<IResponseBody> queue = new ArrayBlockingQueue<>(1_000_000);
    }

    void load(Log log) throws InterruptedException;
}