// src/main/java/Crowdspark/Crowdspark/config/ApiVersion.java
// Feature #26 — API Versioning
// Single source of truth for all API route prefixes.
// Controllers use ApiVersion.V1 + "/resource" in @RequestMapping.

package Crowdspark.Crowdspark.config;

public final class ApiVersion {

    private ApiVersion() {}

    /** All public + authenticated API routes */
    public static final String V1       = "/api/v1";

    /** Admin-only routes (under /api/v1 namespace) */
    public static final String V1_ADMIN = "/api/v1/admin";

    /** Auth routes — deliberately unversioned (stable forever) */
    public static final String AUTH     = "/auth";
}
