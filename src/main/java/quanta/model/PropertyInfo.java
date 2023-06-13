package quanta.model;

/**
 * Holds the value of a single property (i.e. a property 'value' on a Node)
 */
public class PropertyInfo {

    private String name;
    private Object value;

    public PropertyInfo(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return this.name;
    }

    public Object getValue() {
        return this.value;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public void setValue(final Object value) {
        this.value = value;
    }

    public PropertyInfo() {}
}
