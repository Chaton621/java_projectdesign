package com.library.server.recommend;

import java.util.ArrayList;
import java.util.List;

/**
 * 推荐理由
 * 解释为什么推荐某本书
 */
public class RecommendationExplanation {
    private Long bookId;
    private Double score;
    private List<ExplanationPath> paths;  // 推荐路径
    
    public RecommendationExplanation(Long bookId, Double score) {
        this.bookId = bookId;
        this.score = score;
        this.paths = new ArrayList<>();
    }
    
    public void addPath(ExplanationPath path) {
        paths.add(path);
    }
    
    public Long getBookId() {
        return bookId;
    }
    
    public Double getScore() {
        return score;
    }
    
    public List<ExplanationPath> getPaths() {
        return paths;
    }
    
    /**
     * 获取主要推荐理由（贡献最大的路径）
     */
    public String getMainReason() {
        if (paths.isEmpty()) {
            return "基于协同过滤推荐";
        }
        
        ExplanationPath mainPath = paths.get(0);
        if (mainPath.getType() == ExplanationPath.PathType.CO_BORROWED) {
            return String.format("您借过《%s》，其他用户也借过这本书和《%s》", 
                    mainPath.getSourceBookTitle(), mainPath.getTargetBookTitle());
        } else {
            return String.format("与您有相似借阅偏好的用户借过《%s》", 
                    mainPath.getTargetBookTitle());
        }
    }
    
    /**
     * 推荐路径
     */
    public static class ExplanationPath {
        public enum PathType {
            CO_BORROWED,  // 共借路径：User -> Book1 -> User -> Book2
            SIMILAR_USER  // 相似用户路径：User1 -> Book -> User2
        }
        
        private PathType type;
        private Long sourceBookId;  // 源图书ID（共借路径）
        private String sourceBookTitle;
        private Long targetBookId;  // 目标图书ID
        private String targetBookTitle;
        private Double contribution;  // 贡献度
        
        public ExplanationPath(PathType type, Long targetBookId, String targetBookTitle, Double contribution) {
            this.type = type;
            this.targetBookId = targetBookId;
            this.targetBookTitle = targetBookTitle;
            this.contribution = contribution;
        }
        
        public PathType getType() {
            return type;
        }
        
        public Long getSourceBookId() {
            return sourceBookId;
        }
        
        public void setSourceBookId(Long sourceBookId) {
            this.sourceBookId = sourceBookId;
        }
        
        public String getSourceBookTitle() {
            return sourceBookTitle;
        }
        
        public void setSourceBookTitle(String sourceBookTitle) {
            this.sourceBookTitle = sourceBookTitle;
        }
        
        public Long getTargetBookId() {
            return targetBookId;
        }
        
        public String getTargetBookTitle() {
            return targetBookTitle;
        }
        
        public Double getContribution() {
            return contribution;
        }
    }
}















