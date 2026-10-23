import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Security;
import java.time.Duration;
import javax.net.ssl.SSLException;

/**
 * Checks whether the JVM accepts a TLS chain that contains a compromised
 * intermediate CA. If the connection succeeds, the problem was not detected.
 *
 * Usage:
 *  java CheckCompromisedCa.java        (JVM defaults)
 *  java CheckCompromisedCa.java -r     (additionally enable OCSP/CRL revocation checks)
*/
public class CheckCompromisedCa {

    private static final String TEST_URLS[] = {
            "https://dv-g3b-emudhra.we-have-a-problem.com/",
            "https://dv-g3c-emudhra.we-have-a-problem.com/",
            "https://ov-g3b-emudhra.we-have-a-problem.com/",
            "https://ov-g3c-emudhra.we-have-a-problem.com/",
            "https://ev-g3b-emudhra.we-have-a-problem.com/",
            "https://ev-g3c-emudhra.we-have-a-problem.com/",
            "https://dv-g3b-aaa-emudhra.we-have-a-problem.com/",
            "https://dv-g3c-aaa-emudhra.we-have-a-problem.com/",
            "https://ov-g3b-aaa-emudhra.we-have-a-problem.com/",
            "https://ov-g3c-aaa-emudhra.we-have-a-problem.com/",
            "https://ev-g3b-aaa-emudhra.we-have-a-problem.com/",
            "https://ev-g3c-aaa-emudhra.we-have-a-problem.com/"
    };

    public static void main(String[] args) throws InterruptedException {
        if (args.length > 0 && args[0].equals("-r")) {
            // Must be set before the first TLS context is created
            Security.setProperty("ocsp.enable", "true");
            System.setProperty("com.sun.net.ssl.checkRevocation", "true");
            System.setProperty("com.sun.security.enableCRLDP", "true");
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        for (String url : TEST_URLS) {
            verifyTLS(client, url);
        }
    }

    private static void verifyTLS(HttpClient client, String url) throws InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            // Handshake succeeded -> certificate chain was accepted
            System.out.println("VULNERABLE (HTTP " + response.statusCode() + ") " + url);
        } catch (IOException e) {
            SSLException ssl = findSslException(e);
            if (ssl != null) {
                // Handshake was rejected -> problem detected
                System.out.println("OK " + url);
                System.out.println("  " + ssl.getClass().getSimpleName() + ": " + ssl.getMessage());
                return;
            }
            // DNS, timeout, connection refused etc. -> says nothing about TLS
            System.out.println("ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // The HTTP client may wrap the SSL exception, so walk the cause chain
    private static SSLException findSslException(Throwable t) {
        while (t != null) {
            if (t instanceof SSLException) {
                return (SSLException) t;
            }
            t = t.getCause();
        }
        return null;
    }
}