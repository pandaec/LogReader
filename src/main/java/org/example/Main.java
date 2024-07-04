package org.example;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new RootHandler());
        server.createContext("/sse", new SSEHandler());
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        System.out.println("Server started on port 8000");
    }

    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response =
                    "<!DOCTYPE html>" +
                            "<html>" +
                            "<head>" +
                            "    <style>body{font-family: monospace;}</style>" +
                            "    <script src='https://unpkg.com/htmx.org@2.0.0'></script>" +
                            "    <script src='https://unpkg.com/htmx-ext-sse@2.0.0/sse.js'></script>" +
                            "</head>" +
                            "<body>" +
                            "    <div hx-ext='sse' sse-connect='/sse'>" +
                            "        <div id='messages' sse-swap='log-message' hx-swap='beforeend'>" +
                            "            Waiting for updates..." +
                            "        </div>" +
                            "    </div>" +
                            "</body>" +
                            "</html>";

            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    static class SSEHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.getResponseHeaders().add("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);

            try (BufferedReader reader = Files.newBufferedReader(Paths.get("static/dummy.log"));
                 OutputStream os = exchange.getResponseBody()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = "<div>"+escapeXml(line)+"</div>";
                    String message = "event: log-message\n" +  // Specify the event name
                            "data: " + line + "\n\n";  // The actual data

                    os.write(message.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    Thread.sleep(100);
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
}