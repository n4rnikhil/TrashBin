package app.trashbin.common.security;

import java.util.Base64;

/**
 * Validation of configured signing keys. Misconfiguration fails startup instead of weakening
 * security.
 */
public final class Secrets {

    public static final int MIN_KEY_BYTES = 32;

    private Secrets() {}

    /**
     * Decodes a Base64 key and enforces the minimum length.
     *
     * @param envName the environment variable name, used in the error message only
     */
    public static byte[] decodeKey(String base64, String envName) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64 == null ? "" : base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(envName + " must be Base64-encoded", e);
        }
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    envName + " must decode to at least " + MIN_KEY_BYTES + " bytes");
        }
        return key;
    }
}
