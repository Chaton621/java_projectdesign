package com.library.server.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 图书向量嵌入数据访问对象
 * 支持pgvector和fallback方案（float8[]）
 */
public class EmbeddingDao extends BaseDao {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingDao.class);
    private static final boolean USE_PGVECTOR = checkPgVectorSupport();
    
    /**
     * 检查是否支持pgvector
     */
    private static boolean checkPgVectorSupport() {
        try (Connection conn = DataSourceProvider.getDataSource().getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'vector')");
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getBoolean(1)) {
                logger.info("检测到pgvector扩展，使用vector类型");
                return true;
            } else {
                logger.info("未检测到pgvector扩展，使用float8[]数组");
                return false;
            }
        } catch (SQLException e) {
            logger.warn("检查pgvector支持失败，使用fallback方案", e);
            return false;
        }
    }
    
    /**
     * 插入或更新图书向量嵌入（upsert）
     */
    public boolean upsert(Long bookId, float[] embedding, String modelName) {
        return insertOrUpdateEmbedding(bookId, embedding, modelName);
    }
    
    /**
     * 插入或更新图书向量嵌入
     */
    public boolean insertOrUpdateEmbedding(Long bookId, float[] embedding, String modelName) {
        String sql;
        if (USE_PGVECTOR) {
            // 使用pgvector
            sql = "INSERT INTO book_embeddings (book_id, embedding, model_name, updated_at) " +
                  "VALUES (?, ?::vector, ?, ?) " +
                  "ON CONFLICT (book_id) DO UPDATE SET embedding = ?::vector, model_name = ?, updated_at = ?";
        } else {
            // 使用float8[]数组
            sql = "INSERT INTO book_embeddings (book_id, embedding, model_name, updated_at) " +
                  "VALUES (?, ?, ?, ?) " +
                  "ON CONFLICT (book_id) DO UPDATE SET embedding = ?, model_name = ?, updated_at = ?";
        }
        
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, bookId);
            
            if (USE_PGVECTOR) {
                // pgvector格式：'[1,2,3]'
                stmt.setString(2, arrayToString(embedding));
                stmt.setString(3, modelName);
                stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                stmt.setString(5, arrayToString(embedding));
                stmt.setString(6, modelName);
                stmt.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            } else {
                // 使用PostgreSQL数组类型
                stmt.setArray(2, conn.createArrayOf("float8", floatArrayToDoubleArray(embedding)));
                stmt.setString(3, modelName);
                stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                stmt.setArray(5, conn.createArrayOf("float8", floatArrayToDoubleArray(embedding)));
                stmt.setString(6, modelName);
                stmt.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            }
            
            int rows = stmt.executeUpdate();
            logger.info("插入/更新向量嵌入: bookId={}, model={}, affectedRows={}", bookId, modelName, rows);
            return rows > 0;
        } catch (SQLException e) {
            logger.error("插入/更新向量嵌入失败: bookId={}", bookId, e);
            throw new RuntimeException("插入/更新向量嵌入失败", e);
        } finally {
            close(conn, stmt);
        }
    }
    
    /**
     * 根据向量相似度查找TopK相似图书
     * 使用pgvector的<->距离排序，如果不可用则fallback用cosine手算
     */
    public List<SimilarBook> querySimilarBooks(float[] queryVector, int topK) {
        if (USE_PGVECTOR) {
            return querySimilarBooksWithPgVector(queryVector, topK);
        } else {
            return querySimilarBooksWithCosine(queryVector, topK);
        }
    }
    
    /**
     * 使用pgvector查询相似图书
     */
    private List<SimilarBook> querySimilarBooksWithPgVector(float[] queryVector, int topK) {
        String sql = "SELECT book_id, 1 - (embedding <=> ?::vector) as similarity " +
                     "FROM book_embeddings " +
                     "ORDER BY embedding <=> ?::vector " +
                     "LIMIT ?";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        List<SimilarBook> results = new ArrayList<>();
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            String vectorStr = arrayToString(queryVector);
            stmt.setString(1, vectorStr);
            stmt.setString(2, vectorStr);
            stmt.setInt(3, topK);
            
            rs = stmt.executeQuery();
            while (rs.next()) {
                SimilarBook similarBook = new SimilarBook();
                similarBook.bookId = rs.getLong("book_id");
                similarBook.similarity = rs.getDouble("similarity");
                results.add(similarBook);
            }
            logger.info("pgvector相似度查询: topK={}, found={}", topK, results.size());
            return results;
        } catch (SQLException e) {
            logger.error("pgvector相似度查询失败", e);
            throw new RuntimeException("pgvector相似度查询失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 使用cosine相似度手算（fallback方案）
     * 从DB取部分向量，计算cosine相似度
     */
    private List<SimilarBook> querySimilarBooksWithCosine(float[] queryVector, int topK) {
        // 获取向量数据（限制1000条）
        String sql = "SELECT book_id, embedding FROM book_embeddings LIMIT 1000";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        List<SimilarBook> results = new ArrayList<>();
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                Long bookId = rs.getLong("book_id");
                float[] embedding = getEmbeddingFromResultSet(rs);
                
                if (embedding != null && embedding.length == queryVector.length) {
                    double similarity = cosineSimilarity(queryVector, embedding);
                    SimilarBook similarBook = new SimilarBook();
                    similarBook.bookId = bookId;
                    similarBook.similarity = similarity;
                    results.add(similarBook);
                }
            }
            
            // 按相似度排序，取TopK
            results.sort((a, b) -> Double.compare(b.similarity, a.similarity));
            if (results.size() > topK) {
                results = results.subList(0, topK);
            }
            
            logger.info("cosine相似度查询: topK={}, found={}", topK, results.size());
            return results;
        } catch (SQLException e) {
            logger.error("cosine相似度查询失败", e);
            throw new RuntimeException("cosine相似度查询失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 从ResultSet获取向量
     */
    private float[] getEmbeddingFromResultSet(ResultSet rs) throws SQLException {
        if (USE_PGVECTOR) {
            String vectorStr = rs.getString("embedding");
            return stringToArray(vectorStr);
        } else {
            java.sql.Array array = rs.getArray("embedding");
            if (array == null) {
                return null;
            }
            Double[] doubles = (Double[]) array.getArray();
            return doubleArrayToFloatArray(doubles);
        }
    }
    
    /**
     * 计算cosine相似度
     */
    private double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        
        double denominator = Math.sqrt(norm1) * Math.sqrt(norm2);
        if (denominator == 0.0) {
            return 0.0;
        }
        
        return dotProduct / denominator;
    }
    
    /**
     * 根据向量相似度查找TopK相似图书（使用pgvector）
     * 如果使用fallback方案，需要应用层计算相似度
     * @deprecated 使用querySimilarBooks代替
     */
    @Deprecated
    public List<Long> topKSimilarBooksByVector(float[] queryVector, int topK) {
        List<SimilarBook> results = querySimilarBooks(queryVector, topK);
        return results.stream().map(r -> r.bookId).collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 相似图书结果
     */
    public static class SimilarBook {
        public Long bookId;
        public Double similarity;
    }
    
    /**
     * 获取图书向量（用于fallback方案的相似度计算）
     */
    public float[] getEmbedding(Long bookId) {
        String sql = "SELECT embedding FROM book_embeddings WHERE book_id = ?";
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, bookId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                if (USE_PGVECTOR) {
                    // pgvector返回字符串格式
                    String vectorStr = rs.getString("embedding");
                    return stringToArray(vectorStr);
                } else {
                    // float8[]返回数组
                    java.sql.Array array = rs.getArray("embedding");
                    Double[] doubles = (Double[]) array.getArray();
                    return doubleArrayToFloatArray(doubles);
                }
            }
            return null;
        } catch (SQLException e) {
            logger.error("获取向量失败: bookId={}", bookId, e);
            throw new RuntimeException("获取向量失败", e);
        } finally {
            close(conn, stmt, rs);
        }
    }
    
    /**
     * 将float数组转换为字符串（pgvector格式）
     */
    private String arrayToString(float[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * 将字符串转换为float数组（pgvector格式）
     */
    private float[] stringToArray(String str) {
        str = str.trim();
        if (str.startsWith("[") && str.endsWith("]")) {
            str = str.substring(1, str.length() - 1);
        }
        String[] parts = str.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
    
    /**
     * float数组转Double数组
     */
    private Double[] floatArrayToDoubleArray(float[] floats) {
        Double[] doubles = new Double[floats.length];
        for (int i = 0; i < floats.length; i++) {
            doubles[i] = (double) floats[i];
        }
        return doubles;
    }
    
    /**
     * Double数组转float数组
     */
    private float[] doubleArrayToFloatArray(Double[] doubles) {
        float[] floats = new float[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            floats[i] = doubles[i].floatValue();
        }
        return floats;
    }
}

