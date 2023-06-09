package quanta.util;

import java.util.LinkedList;
import quanta.mongo.model.SubNode;

public class TreeNode {
    public TreeNode(SubNode node) {
        this.node = node;
    }

    public SubNode node;
    public LinkedList<TreeNode> children;
}