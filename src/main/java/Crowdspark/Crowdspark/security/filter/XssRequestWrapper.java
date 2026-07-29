// src/main/java/Crowdspark/Crowdspark/security/filter/XssRequestWrapper.java
package Crowdspark.Crowdspark.security.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Wraps HttpServletRequest to strip dangerous HTML/JS patterns from JSON
 * request bodies before they reach controllers.
 *
 * AUDIT FIX (Feature #14): the original version ran the regexes below
 * directly against the RAW body string, before it was ever parsed as JSON —
 * meaning every field in every request got the same blind find-and-replace,
 * including "password", "currentPassword", "razorpaySignature",
 * "transactionId", etc. Concretely: a password like "Online=Banking1" would
 * silently lose its "Online=" prefix (it matches \bon\w+\s*=) before it was
 * ever hashed.
 *
 * REVISION 2: the first fix for this parsed the body with Jackson's JsonNode
 * tree API (ObjectNode/ArrayNode, walked via .fields()). That broke, because
 * Jackson 3.0 removed JsonNode.fields() in favor of .properties() — and
 * digging further, isTextual()/asText() and the array/object node-creation
 * methods were ALSO mid-rename in that exact release (Jackson's own migration
 * guide flags 3.0.0 itself as "not an LTS version... a transitional release").
 * Rather than chase each individually-renamed tree-node method, this version
 * avoids the JsonNode tree API entirely: it deserializes the body into plain
 * java.util.Map/List/String (via the single most foundational, unchanged-
 * since-Jackson-1.0 ObjectMapper methods — readValue()/writeValueAsBytes()),
 * walks THAT with plain java.util.Map/List calls, and re-serializes. No
 * Jackson-version-specific method names anywhere in the walk itself.
 *
 * Anything that isn't JSON (or fails to parse as JSON) is passed through
 * completely untouched rather than regex-mangled on unknown structure — a
 * blocklist regex over arbitrary text is a known-fragile approach anyway
 * (OWASP recommends output encoding instead); the real defenses against
 * stored XSS here are React's default escaping on render and the
 * Content-Security-Policy already configured in SecurityConfig.
 */
public class XssRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] sanitizedBody;

    // Field names (case-insensitive) that must reach the controller byte-for-byte.
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "currentpassword", "newpassword", "confirmpassword",
            "oldpassword", "token", "accesstoken", "refreshtoken", "idtoken",
            "code", "otp", "totpcode", "backupcode",
            "signature", "razorpaysignature", "razorpayorderid",
            "razorpaypaymentid", "transactionid", "secret"
    );

    private static final Pattern[] XSS_PATTERNS = {
        Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("<script[^>]*/>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bon\\w+\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("data\\s*:\\s*text/(html|javascript)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<(iframe|object|embed|applet|form|input|button|base|link|meta)[^>]*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("expression\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vbscript\\s*:", Pattern.CASE_INSENSITIVE),
    };

    public XssRequestWrapper(HttpServletRequest request, ObjectMapper objectMapper) throws IOException {
        super(request);
        byte[] raw = request.getInputStream().readAllBytes();

        String contentType = request.getContentType();
        boolean looksLikeJson = contentType != null && contentType.toLowerCase().contains("json");

        if (raw.length == 0 || !looksLikeJson) {
            this.sanitizedBody = raw;
            return;
        }

        byte[] cleaned;
        try {
            // Deserializes JSON objects/arrays into plain LinkedHashMap /
            // ArrayList, and scalars into String / Number / Boolean / null —
            // this is Jackson's default "untyped" deserialization behavior
            // and has been stable since Jackson 1.x.
            Object root = objectMapper.readValue(raw, Object.class);
            Object sanitized = sanitizeValue(root, null);
            cleaned = objectMapper.writeValueAsBytes(sanitized);
        } catch (Exception e) {
            // Not valid JSON, or something else went wrong — fail safe by
            // passing the original body through unmodified rather than
            // guessing at it with regex.
            cleaned = raw;
        }
        this.sanitizedBody = cleaned;
    }

    /**
     * Recursively rebuilds the parsed JSON value, sanitizing only String
     * leaves whose enclosing field name isn't in SENSITIVE_FIELDS. Works
     * purely in terms of java.util.Map / java.util.List / String — no
     * Jackson-tree-API types or method names involved anywhere here.
     */
    @SuppressWarnings("unchecked")
    private static Object sanitizeValue(Object value, String fieldName) {
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (val instanceof String strVal) {
                    result.put(key, SENSITIVE_FIELDS.contains(key.toLowerCase())
                            ? strVal : sanitize(strVal));
                } else {
                    result.put(key, sanitizeValue(val, key));
                }
            }
            return result;
        }

        if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            List<Object> result = new ArrayList<>(list.size());
            boolean fieldIsSensitive = fieldName != null && SENSITIVE_FIELDS.contains(fieldName.toLowerCase());
            for (Object item : list) {
                if (item instanceof String strItem) {
                    result.add(fieldIsSensitive ? strItem : sanitize(strItem));
                } else {
                    result.add(sanitizeValue(item, fieldName));
                }
            }
            return result;
        }

        // Numbers, Booleans, null: returned as-is, nothing to sanitize.
        return value;
    }

    // ── Parameter sanitization (query string / form params) ───────────────────

    @Override
    public String getParameter(String name) {
        String val = super.getParameter(name);
        if (val == null) return null;
        return SENSITIVE_FIELDS.contains(name.toLowerCase()) ? val : sanitize(val);
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] vals = super.getParameterValues(name);
        if (vals == null) return null;
        if (SENSITIVE_FIELDS.contains(name.toLowerCase())) return vals;
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

    // ── Body sanitization ──────────────────────────────────────────────────────

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

    // ── Core sanitizer (unchanged regex set; now only ever applied to
    //    non-sensitive string values instead of the whole raw body) ───────────

    public static String sanitize(String input) {
        if (input == null || input.isBlank()) return input;
        String result = input;
        for (Pattern pattern : XSS_PATTERNS) {
            result = pattern.matcher(result).replaceAll("");
        }
        return result;
    }
}
