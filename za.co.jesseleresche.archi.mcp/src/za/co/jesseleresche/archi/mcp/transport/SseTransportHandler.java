package za.co.jesseleresche.archi.mcp.transport;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * SSE transport endpoint ({@code GET /sse}).
 * <p>
 * Opens a persistent Server-Sent Events stream. The client keeps this connection
 * open for the lifetime of the session. An endpoint event is sent immediately,
 * telling the client where to POST messages.
 */
public class SseTransportHandler extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final ConcurrentHashMap<String, AsyncContext> sseSessions;

    public SseTransportHandler(ConcurrentHashMap<String, AsyncContext> sseSessions) {
        this.sseSessions = sseSessions;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.flushBuffer();

        String sessionId = UUID.randomUUID().toString();
        AsyncContext async = request.startAsync();
        async.setTimeout(0); // no timeout

        sseSessions.put(sessionId, async);

        PrintWriter writer = response.getWriter();

        // Send endpoint event
        writer.write("event: endpoint\n");
        writer.write("data: /message?sessionId=" + sessionId + "\n\n");
        writer.flush();

        // Register cleanup on disconnect
        async.addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent e) {
                sseSessions.remove(sessionId);
            }

            @Override
            public void onError(AsyncEvent e) {
                sseSessions.remove(sessionId);
            }

            @Override
            public void onTimeout(AsyncEvent e) {
                sseSessions.remove(sessionId);
            }

            @Override
            public void onStartAsync(AsyncEvent e) {
                // no-op
            }
        });
    }
}
