package com.library.server.recommend;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 图数据结构
 * 存储User-Book二部图
 */
public class Graph {
    private Map<String, GraphNode> nodes;  // nodeId -> GraphNode
    
    public Graph() {
        this.nodes = new HashMap<>();
    }
    
    public void addNode(String nodeId, GraphNode.NodeType type) {
        if (!nodes.containsKey(nodeId)) {
            nodes.put(nodeId, new GraphNode(nodeId, type));
        }
    }
    
    public void addEdge(String fromId, String toId, double weight) {
        GraphNode fromNode = nodes.get(fromId);
        if (fromNode != null) {
            fromNode.addEdge(toId, weight);
        }
    }
    
    public GraphNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }
    
    public Set<String> getAllNodeIds() {
        return nodes.keySet();
    }
    
    public int getNodeCount() {
        return nodes.size();
    }
    
    public boolean containsNode(String nodeId) {
        return nodes.containsKey(nodeId);
    }
}



















