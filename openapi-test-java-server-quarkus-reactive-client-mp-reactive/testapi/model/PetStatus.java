package testapi.model;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum PetStatus {
    available("available"),
    pending("pending"),
    sold("sold");
    final String value;

    public String value() {
        return value;
    }

    PetStatus(String value) {
        this.value = value;
    }

    public static final String Names = Arrays.stream(PetStatus.values()).map(x -> x.value).collect(Collectors.joining(", "));
    public static final Map<String, PetStatus> ByName = Arrays.stream(PetStatus.values()).collect(Collectors.toMap(n -> n.value, n -> n));

    

    public static PetStatus force(String str) {
        if (ByName.containsKey(str)) {
            return ByName.get(str);
        } else {
            throw new RuntimeException("'" + str + "' does not match any of the following legal values: " + Names);
        }
    }
}
