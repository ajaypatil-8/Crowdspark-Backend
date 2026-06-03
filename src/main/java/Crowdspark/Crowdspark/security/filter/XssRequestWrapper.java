// src/main/java/Crowdspark/Crowdspark/security/filter/XssRequestWrapper.java
package Crowdspark.Crowdspark.security.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Wraps HttpServletRequest to strip dangerous HTML/JS patterns
 * from query parameters and JSON body before they reach controllers.
 *
 * Patterns stripped:
 *   <script>...</script>  — inline scripts
 *   javascript:           — JS protocol in URLs
 *   on[event]=            — inline event handlers (onclick, onerror, etc.)
 *   data:text/html        — data URI XSS
 *   <iframe>, <object>    — embedding attacks
 *   expression(           — CSS expression XSS (IE legacy)
 */
public class XssRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] sanitizedBody;

    // ── Patterns to strip ─────────────────────────────────────────────────────
    private static final Pattern[] XSS_PATTERNS = {
        // script tags (case-insensitive, with optional attributes)
        Pattern.compile("<script[^>]*>.*?</script>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("<script[^>]*/>",
                Pattern.CASE_INSENSITIVE),
        // javascript: protocol
        Pattern.compile("javascript\\s*:",
                Pattern.CASE_INSENSITIVE),
        // inline event handlers: onclick=, onload=, onerror=, onmouseover=, etc.
        Pattern.compile("\\bon\\w+\\s*=",
                Pattern.CASE_INSENSITIVE),
        // data URIs used for XSS (only strip html/javascript variants)
        Pattern.compile("data\\s*:\\s*text/(html|javascript)",
                Pattern.CASE_INSENSITIVE),
        // dangerous HTML tags
        Pattern.compile("<(iframe|object|embed|applet|form|input|button|base|link|meta)[^>]*>",
                Pattern.CASE_INSENSITIVE),
        // CSS expression (old IE XSS vector)
        Pattern.compile("expression\\s*\\(",
                Pattern.CASE_INSENSITIVE),
        // vbscript
        Pattern.compile("vbscript\\s*:",
                Pattern.CASE_INSENSITIVE),
    };

    public XssRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        // Read the original body
        byte[] raw = request.getInputStream().readAllBytes();
        String body = new String(raw, StandardCharsets.UTF_8);
        // Sanitize and store
        this.sanitizedBody = sanitize(body).getBytes(StandardCharsets.UTF_8);
    }

    // ── Parameter sanitization (query string / form params) ───────────────────

    @Override
    public String getParameter(String name) {
        String val = super.getParameter(name);
        return val != null ? sanitize(val) : null;
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] vals = super.getParameterValues(name);
        if (vals == null) return null;
        String[] cleaned = new String[vals.length];
        for (int i = 0; i < vals.length; i++) {
            cleaned[i] = sanitize(vals[i]);
        }
        return cleaned;
    }

    @Override
    public String getHeader(String name) {
        // Don't sanitize headers (breaks auth tokens, content-type, etc.)
        return super.getHeader(name);
    }

    // ── Body (JSON) sanitization ──────────────────────────────────────────────

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream bis = new ByteArrayInputStream(sanitizedBody);
        return new ServletInputStream() {
            @Override public int read() { return bis.read(); }
            @Override public boolean isFinished() { return bis.available() == 0; }
            @Override public boolean isReady()    { return true; }
            @Override public void setReadListener(ReadListener listener) { }
        };
    }

    @Override
    public java.io.BufferedReader getReader() {
        return new java.io.BufferedReader(
                new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    // ── Core sanitizer ────────────────────────────────────────────────────────

    public static String sanitize(String input) {
        if (input == null || input.isBlank()) return input;
        String result = input;
        for (Pattern pattern : XSS_PATTERNS) {
            result = pattern.matcher(result).replaceAll("");
        }
        return result;
    }
}
