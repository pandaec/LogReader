package org.example;

import org.example.ILogParser.LoadProgress;
import org.example.ILogParser.LogDetail;

/**
 * IResponse
 */
public interface IResponse {
    public static class ResponseLogDetail {
        String name = "logDetail";
        final LogDetail body;

        public ResponseLogDetail(LogDetail body) {
            this.body = body;
        }

        public String getName() {
            return name;
        }

        public LogDetail getBody() {
            return body;
        }
    }

    public static class ResponseLogProgress {
        String name = "logProgress";
        final LoadProgress body;

        public ResponseLogProgress(LoadProgress body) {
            this.body = body;
        }

        public String getName() {
            return name;
        }

        public LoadProgress getBody() {
            return body;
        }
    }

    public interface IResponseBody {
    }
}
