package org.example;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            String response = Files.readString(Paths.get("static", "index.html"));
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    static class SearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> queryMap = queryToMap(exchange.getRequestURI().getRawQuery());
            String query = queryMap.get("q") != null ? URLDecoder.decode(queryMap.get("q"), StandardCharsets.UTF_8)
                    : "";
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.getResponseHeaders().add("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);

            try (OutputStream os = exchange.getResponseBody()) {
                ILogParser.Log log = ILogParser.Log.load(List.of(Paths.get("static", "dummy.log")));
                log.start();
                int i = 0;
                Map<String, Pattern> patternMap = parseFilter(query, false);

                for (ILogParser.LogDetail line : log.lines) {
                    if (isLineMatchFilter(line, patternMap)) {
                        if (i++ > 1000) {
                            break;
                        }
                        String data = "<div>" + escapeXml(
                                line.priority + ";" + line.threadName + ";" + line.time + ";" + line.getContent())
                                + "</div>";
                        String message = "event: log-message\n" +
                                "data: " + data + "\n\n";

                        os.write(message.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        // Thread.sleep(100);
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
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