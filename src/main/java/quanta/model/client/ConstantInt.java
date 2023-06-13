package quanta.model.client;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ConstantInt {
    ROWS_PER_PAGE(25);

    @JsonValue
    private final Integer value;

    private ConstantInt(Integer value) {
        this.value = value;
    }

    public Integer val() {
        return value;
    }
}