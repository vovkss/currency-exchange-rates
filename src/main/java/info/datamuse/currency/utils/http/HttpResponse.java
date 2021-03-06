package info.datamuse.currency.utils.http;

import info.datamuse.currency.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import static info.datamuse.currency.utils.HttpUtils.BUFFER_SIZE;
import static info.datamuse.currency.utils.HttpUtils.isSuccess;
import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpResponse {
    private static final Logger logger = LoggerFactory.getLogger(HttpResponse.class);

    private final HttpURLConnection connection;

    private int responseCode;
    private String errorString;

    private InputStream inputStream;

    public HttpResponse(final HttpURLConnection connection) {
        this.connection = connection;

        try {
            this.responseCode = this.connection.getResponseCode();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't connect due to a network error.", e);
        }

        if (!isSuccess(this.responseCode)) {
            logger.debug(this.toString());
            String response = null;
            try {
                response = this.getError();
                if (response == null || response.length() == 0) {
                    response = this.getBodyAsString();
                }
            } catch(Exception e) {
                logger.debug("Couldn't get more detailed error information");
            }
            throw new RuntimeException(String.format("Server returned an error code: %s. %s", this.responseCode, response));
        }
        logger.debug(this.toString());
    }

    public int getResponseCode() {
        return responseCode;
    }

    public String getBodyAsString() {
        final InputStream bodyStream = this.getBody();
        final InputStreamReader reader = new InputStreamReader(bodyStream, UTF_8);
        final String bodyAsString;

        try {
            bodyAsString = IOUtils.toString(reader);
            this.disconnect();
        } catch (IOException e) {
            throw new RuntimeException("Couldn't connect due to a network error.", e);
        }  finally {
            IOUtils.closeQuietly(reader);
        }

        return bodyAsString;
    }

    public InputStream getBody() {
        if (this.inputStream == null) {
            try {
                this.inputStream = this.connection.getInputStream();
            } catch (IOException e) {
                throw new RuntimeException("Couldn't connect due to a network error.", e);
            }
        }
        return this.inputStream;
    }

    public String getError() {
        if (this.errorString == null && !isSuccess(this.responseCode)) {
            this.errorString = readErrorStream(this.connection.getErrorStream());
        }
        return this.errorString;
    }

    private static String readErrorStream(final InputStream stream) {
        if (stream == null) {
            return null;
        }

        final InputStreamReader reader = new InputStreamReader(stream, UTF_8);
        final String error;

        try {
            error = IOUtils.toString(reader);

        } catch (IOException e) {
            return null;

        } finally {
            IOUtils.closeQuietly(reader);
        }

        return error;
    }

    public void disconnect() {
        if (this.connection == null) {
            return;
        }

        try {
            if (this.inputStream == null) {
                this.inputStream = this.connection.getInputStream();
            }

            byte[] buffer = new byte[BUFFER_SIZE];
            int n = this.inputStream.read(buffer);
            while (n != IOUtils.EOF) {
                n = this.inputStream.read(buffer);
            }
            this.inputStream.close();

            if (this.inputStream != null) {
                this.inputStream.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("Couldn't finish closing the connection", e);
        }
    }

    @Override
    public String toString() {
        String lineSeparator = System.getProperty("line.separator");
        StringBuilder builder = new StringBuilder();
        builder.append("Http Response").
                append(lineSeparator).
                append(this.connection.getRequestMethod()).
                append(lineSeparator).
                append(this.connection.getURL().toString()).
                append(lineSeparator).
                append(this.connection.getHeaderFields().toString()).toString();
        if (this.errorString != null) {
            builder.append(lineSeparator);
            builder.append(errorString);
        }
        return builder.toString().trim();
    }

}
