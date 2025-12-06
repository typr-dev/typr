package typo.data;

import java.util.Arrays;

/**
 * Represents a pgvector vector type.
 * Format: [1,2,3] (bracketed, comma-separated)
 */
public record Vector(short[] values) {
    public static Vector parse(String value) {
        // Handle both formats: "[1,2,3]" (pgvector format) and "1 2 3" (space-separated)
        String trimmed = value.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            // pgvector format: [1,2,3]
            String inner = trimmed.substring(1, trimmed.length() - 1);
            if (inner.isEmpty()) {
                return new Vector(new short[0]);
            }
            String[] parts = inner.split(",");
            short[] ret = new short[parts.length];
            for (int i = 0; i < parts.length; i++) {
                ret[i] = Short.parseShort(parts[i].trim());
            }
            return new Vector(ret);
        } else {
            // Legacy space-separated format: 1 2 3
            String[] parts = trimmed.split("\\s+");
            short[] ret = new short[parts.length];
            for (int i = 0; i < parts.length; i++) {
                ret[i] = Short.parseShort(parts[i]);
            }
            return new Vector(ret);
        }
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Vector other) {
            if (values.length != other.values.length) {
                return false;
            }
            for (var i = 0; i < values.length; i++) {
                if (values[i] != other.values[i]) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Returns the vector in pgvector format: [1,2,3]
     */
    public String value() {
        var sb = new StringBuilder();
        sb.append("[");
        for (var i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(values[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    public Vector(String value) {
        this(Vector.parse(value).values);
    }
}
