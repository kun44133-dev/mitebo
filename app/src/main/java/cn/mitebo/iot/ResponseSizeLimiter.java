package cn.mitebo.iot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

final class ResponseSizeLimiter {
    static final int MAX_API_RESPONSE_BYTES = 8 * 1024 * 1024;

    private ResponseSizeLimiter() {
    }

    static String readUtf8(HttpURLConnection connection, InputStream stream) throws IOException {
        return readUtf8(connection, stream, MAX_API_RESPONSE_BYTES);
    }

    static String readUtf8(HttpURLConnection connection, InputStream stream, int maxBytes) throws IOException {
        if (stream == null) {
            return "";
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        long declaredLength = connection == null ? -1L : connection.getContentLengthLong();
        if (declaredLength > maxBytes) {
            stream.close();
            throw new ResponseTooLargeException(maxBytes);
        }
        int initialCapacity = declaredLength > 0
                ? (int) Math.min(declaredLength, Math.min(maxBytes, 32 * 1024))
                : 8 * 1024;
        try (InputStream input = stream;
             ByteArrayOutputStream output = new ByteArrayOutputStream(initialCapacity)) {
            byte[] buffer = new byte[8 * 1024];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > maxBytes - total) {
                    throw new ResponseTooLargeException(maxBytes);
                }
                output.write(buffer, 0, count);
                total += count;
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static final class ResponseTooLargeException extends IOException {
        ResponseTooLargeException(int maxBytes) {
            super("response exceeds " + maxBytes + " bytes");
        }
    }
}
