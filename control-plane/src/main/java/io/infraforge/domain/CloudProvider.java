package io.infraforge.domain;

/**
 * Target cloud provider for a user's infrastructure request.
 * infraforge itself always runs on AWS; this field describes where the
 * generated Terraform will be applied.
 */
public enum CloudProvider {
    AWS, GCP, AZURE;

    /** Case-insensitive parse — throws {@link IllegalArgumentException} on unknown values. */
    public static CloudProvider fromString(String value) {
        return valueOf(value.toUpperCase());
    }
}
