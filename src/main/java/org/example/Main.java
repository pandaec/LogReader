package org.example;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Main {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new RootHandler());
        server.createContext("/search", new SearchHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Server started on port 8000");
    }

    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String uriPath = exchange.getRequestURI().getPath();

            if (uriPath.equals("/") || uriPath.isEmpty()) {
                uriPath = "/index.html";
            }

            Path filePath = Paths.get("static", uriPath);

            if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                String contentType = getContentType(filePath);
                byte[] response = Files.readAllBytes(filePath);

                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, response.length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } else {
                String response = "404 (Not Found)\n";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }

    static class SearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Map<String, String> queryMap = queryToMap(exchange.getRequestURI().getRawQuery());
                String query = queryMap.get("q") != null ? URLDecoder.decode(queryMap.get("q"), StandardCharsets.UTF_8)
                        : "";
                String fromFile = queryMap.get("from") != null
                        ? URLDecoder.decode(queryMap.get("from"), StandardCharsets.UTF_8)
                        : null;

                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.getResponseHeaders().add("Cache-Control", "no-cache");
                exchange.getResponseHeaders().add("Connection", "keep-alive");
                exchange.sendResponseHeaders(200, 0);

                try (OutputStream os = exchange.getResponseBody()) {
                    List<Path> logPaths = new ArrayList<>();
                    try {
                        Path path = Paths.get("static", "logs");
                        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                            for (Path entry : stream) {
                                if (Files.isRegularFile(entry)) {
                                    logPaths.add(entry);
                                }
                            }
                        }
                    } catch (InvalidPathException | IOException e) {
                        e.printStackTrace();
                    }
                    Collections.sort(logPaths);
                    if (fromFile != null) {
                        int idx = -1;
                        Path fromPath = Paths.get(fromFile);
                        for (int i = 0; i < logPaths.size(); i++) {
                            Path path = logPaths.get(i);
                            if (fromPath.normalize().equals(path.normalize())) {
                                idx = i;
                                break;
                            }
                        }
                        if (idx > -1) {
                            if (idx + 1 < logPaths.size()) {
                                logPaths = logPaths.subList(idx + 1, logPaths.size());
                            } else {
                                logPaths = new ArrayList<>();
                            }
                        }
                    }
                    if (logPaths.size() > 3) {
                        logPaths = logPaths.subList(0, 3);
                    }

                    Map<String, Pattern> filter = parseFilter(query, false);
                    ILogParser.Log log = ILogParser.Log.load(logPaths, filter);
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.submit(() -> {
                        try {
                            log.start();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    });

                    ObjectMapper objectMapper = new ObjectMapper();
                    while (log.loadQueue.isLoading.get() || !log.loadQueue.queue.isEmpty()) {
                        ILogParser.LogDetail detail = log.loadQueue.queue.poll(250, TimeUnit.MILLISECONDS);
                        if (detail != null) {
                            String json = objectMapper.writeValueAsString(detail);
                            String message = "event: log-message\n" +
                                    "data: " + json + "\n\n";

                            os.write(message.getBytes(StandardCharsets.UTF_8));
                            os.flush();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                String response = "Internal server error";
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(500, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    public static String escapeXml(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder escaped = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '&':
                    escaped.append("&amp;");
                    break;
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '\"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&apos;");
                    break;
                default:
                    escaped.append(c);
            }
        }
        return escaped.toString();
    }

    public static Map<String, String> queryToMap(String query) {
        if (query == null) {
            return new HashMap<>();
        }
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length > 1) {
                result.put(pair[0], pair[1]);
            } else {
                result.put(pair[0], "");
            }
        }
        return result;
    }

    static Map<String, Pattern> parseFilter(String query, boolean isCaseInsensitive) {
        String filterStr = query;
        Map<String, Pattern> mapPattern = new HashMap<>();

        int colStart = 0;
        int colEnd = 0;
        int condStart = 0;
        int condEnd = 0;

        try {
            int regexFlag = 0;
            // if (!filter.imIsCaseSensitive.get()) {
            // regexFlag |= Pattern.CASE_INSENSITIVE;
            // }
            if (isCaseInsensitive) {
                regexFlag |= Pattern.CASE_INSENSITIVE;
            }

            // Parsing logic could be done in regex instead
            for (int i = 0; i < filterStr.length(); i++) {
                char c = filterStr.charAt(i);
                if (colEnd < 1) {
                    for (; i < filterStr.length() && filterStr.charAt(i) == ' '; i++)
                        ;
                    colStart = i;
                    for (; i + 1 < filterStr.length() && filterStr.charAt(i + 1) != ' '
                            && filterStr.charAt(i + 1) != '='; i++)
                        ;
                    colEnd = i + 1;
                } else if (condStart < 1) {
                    if (c == '\'' || c == '"') {
                        i = i + 1;
                        condStart = i;
                        for (; i < filterStr.length()
                                && (filterStr.charAt(i) != '\'' && filterStr.charAt(i) != '"'); i++)
                            ;
                        condEnd = i;
                        String colName = filterStr.substring(colStart, colEnd);
                        Pattern pattern = Pattern.compile("^.*" + filterStr.substring(condStart, condEnd) + ".*$",
                                regexFlag);
                        mapPattern.put(colName.toUpperCase(), pattern);
                    }
                } else if (condEnd > 0) {
                    if (i + 2 < filterStr.length()) {
                        String s = filterStr.substring(i, i + 3);
                        if (s.equalsIgnoreCase("AND")) {
                            i = i + 3;
                            for (; i + 1 < filterStr.length() && filterStr.charAt(i + 1) == ' '; i++)
                                ;
                            colStart = i + 1;
                            colEnd = 0;
                            condStart = 0;
                            condEnd = 0;
                        }
                    }
                }
                for (; i + 1 < filterStr.length() && filterStr.charAt(i + 1) == ' '; i++)
                    ;
            }

            if (mapPattern.isEmpty()) {
                Pattern pattern = Pattern.compile("^.*" + filterStr + ".*$", regexFlag);
                mapPattern.put("*", pattern);
            }
        } catch (PatternSyntaxException e) {
            e.printStackTrace();
            // filter.isRegexError = true;
        }
        return mapPattern;
    }

    private static String getContentType(Path filePath) {
        String fileName = filePath.getFileName().toString();
        if (fileName.endsWith(".html")) {
            return "text/html";
        } else if (fileName.endsWith(".js")) {
            return "application/javascript";
        } else if (fileName.endsWith(".css")) {
            return "text/css";
        } else if (fileName.endsWith(".json")) {
            return "application/json";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else {
            return "application/octet-stream";
        }
    }
}