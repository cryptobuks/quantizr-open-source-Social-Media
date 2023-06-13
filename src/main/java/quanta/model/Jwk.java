package quanta.model;

import com.fasterxml.jackson.annotation.JsonProperty;

// just for holding modulus(n) and exponent(e)
public class Jwk {

    private String e;
    private String n;

    @JsonProperty("e")
    public String getE() {
        return e;
    }

    @JsonProperty("e")
    public void setE(String e) {
        this.e = e;
    }

    @JsonProperty("n")
    public String getN() {
        return n;
    }

    @JsonProperty("n")
    public void setN(String n) {
        this.n = n;
    }
}
