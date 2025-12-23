package com.library.server.recommend;

import java.util.HashMap;
import java.util.Map;

/**
 * 图节点
 * 表示User或Book节点
 */
public class GraphNode {
    public enum NodeType {
        USER, BOOK
    }
    
    private final String id;
    private final NodeType type;
    private Map<String, Double> edges;  // 邻接边：targetId -> weight
    
    public GraphNode(String id, NodeType type) {
        this.id = id;
        this.type = type;
        this.edges = new HashMap<>();
    }
    
    public void addEdge(String targetId, double weight) {
        edges.put(targetId, edges.getOrDefault(targetId, 0.0) + weight);
    }
    
    public String getId() {
        return id;
    }
    
    public NodeType getType() {
        return type;
    }
    
    public Map<String, Double> getEdges() {
        return edges;
    }
    
    public double getTotalEdgeWeight() {
        return edges.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}



















