
package quanta.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import quanta.model.client.NodeLink;
import org.springframework.data.annotation.Transient;

public class GraphNode {
    private String id;
    private int level;
    private boolean highlight;
    private String name;
    private String path;
    private List<GraphNode> children;
    private HashSet<String> childIds;
    private HashMap<String, NodeLink> links;

    public GraphNode(String id, String name, String path, int level, boolean highlight, HashMap<String, NodeLink> links) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.level = level;
        this.highlight = highlight;
        this.links = links;
    }

    public void addChild(GraphNode child) {
        if (childIds != null && childIds.contains(child.getId())) return;
        if (children == null) {
            children = new LinkedList<>();
        }
        children.add(child);
        if (childIds == null) {
            childIds = new HashSet<>();
        }
        childIds.add(child.getId());
    }

    @Transient
    @JsonIgnore
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Transient
    @JsonIgnore
    public HashSet<String> getChildIds() {
        return childIds;
    }

    public void setChildIds(HashSet<String> childIds) {
        this.childIds = childIds;
    }

    
    public String getId() {
        return this.id;
    }

    
    public int getLevel() {
        return this.level;
    }

    
    public boolean isHighlight() {
        return this.highlight;
    }

    
    public String getName() {
        return this.name;
    }

    
    public List<GraphNode> getChildren() {
        return this.children;
    }

    
    public HashMap<String, NodeLink> getLinks() {
        return this.links;
    }

    
    public void setId(final String id) {
        this.id = id;
    }

    
    public void setLevel(final int level) {
        this.level = level;
    }

    
    public void setHighlight(final boolean highlight) {
        this.highlight = highlight;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setChildren(final List<GraphNode> children) {
        this.children = children;
    }

    
    public void setLinks(final HashMap<String, NodeLink> links) {
        this.links = links;
    }
    
    public GraphNode() {
    }
}
