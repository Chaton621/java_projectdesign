package com.library.server.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

/**
 * 图书封面生成工具
 * 自动生成带书名的封面图片
 */
public class BookCoverGenerator {
    private static final int COVER_WIDTH = 300;
    private static final int COVER_HEIGHT = 400;
    private static final String COVER_DIR = "covers";
    
    // 封面背景颜色（根据分类选择不同颜色）
    private static final Color[] CATEGORY_COLORS = {
        new Color(102, 126, 234),  // 计算机 - 蓝紫色
        new Color(118, 75, 162),   // 科幻小说 - 紫色
        new Color(231, 76, 60),    // 文学 - 红色
        new Color(52, 152, 219),   // 历史 - 蓝色
        new Color(46, 204, 113),   // 心理学 - 绿色
        new Color(241, 196, 15),   // 经济管理 - 黄色
        new Color(155, 89, 182),   // 悬疑小说 - 紫红色
        new Color(230, 126, 34)    // 其他 - 橙色
    };
    
    /**
     * 生成图书封面
     * @param bookId 图书ID
     * @param title 书名
     * @param author 作者
     * @param category 分类
     * @return 封面图片路径，如果生成失败返回null
     */
    public static String generateCover(Long bookId, String title, String author, String category) {
        try {
            // 确保封面目录存在
            Path coverDir = Paths.get(COVER_DIR);
            if (!Files.exists(coverDir)) {
                Files.createDirectories(coverDir);
            }
            
            // 创建封面图片
            BufferedImage image = new BufferedImage(COVER_WIDTH, COVER_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();
            
            // 设置抗锯齿
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            // 选择背景颜色（根据分类）
            Color bgColor = getColorForCategory(category);
            
            // 绘制渐变背景
            GradientPaint gradient = new GradientPaint(
                0, 0, bgColor,
                COVER_WIDTH, COVER_HEIGHT, bgColor.darker()
            );
            g2d.setPaint(gradient);
            g2d.fillRect(0, 0, COVER_WIDTH, COVER_HEIGHT);
            
            // 添加装饰性图案
            drawDecorativePattern(g2d, bgColor);
            
            // 绘制书名
            drawTitle(g2d, title, COVER_WIDTH, COVER_HEIGHT);
            
            // 绘制作者名（较小）
            if (author != null && !author.isEmpty()) {
                drawAuthor(g2d, author, COVER_WIDTH, COVER_HEIGHT);
            }
            
            g2d.dispose();
            
            // 保存图片
            String filename = "book_" + bookId + ".png";
            Path filePath = coverDir.resolve(filename);
            ImageIO.write(image, "PNG", filePath.toFile());
            
            return filePath.toString();
            
        } catch (IOException e) {
            System.err.println("生成封面失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 根据分类获取颜色
     */
    private static Color getColorForCategory(String category) {
        if (category == null) {
            return CATEGORY_COLORS[7]; // 默认橙色
        }
        
        String cat = category.toLowerCase();
        if (cat.contains("计算机") || cat.contains("编程") || cat.contains("技术")) {
            return CATEGORY_COLORS[0];
        } else if (cat.contains("科幻")) {
            return CATEGORY_COLORS[1];
        } else if (cat.contains("文学")) {
            return CATEGORY_COLORS[2];
        } else if (cat.contains("历史")) {
            return CATEGORY_COLORS[3];
        } else if (cat.contains("心理")) {
            return CATEGORY_COLORS[4];
        } else if (cat.contains("经济") || cat.contains("管理")) {
            return CATEGORY_COLORS[5];
        } else if (cat.contains("悬疑") || cat.contains("推理")) {
            return CATEGORY_COLORS[6];
        } else {
            return CATEGORY_COLORS[7];
        }
    }
    
    /**
     * 绘制装饰性图案
     */
    private static void drawDecorativePattern(Graphics2D g2d, Color baseColor) {
        g2d.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 30));
        
        // 绘制一些圆形装饰
        for (int i = 0; i < 5; i++) {
            int x = (int) (Math.random() * COVER_WIDTH);
            int y = (int) (Math.random() * COVER_HEIGHT);
            int radius = (int) (20 + Math.random() * 40);
            g2d.fillOval(x, y, radius, radius);
        }
        
        // 绘制线条装饰
        g2d.setStroke(new BasicStroke(2));
        for (int i = 0; i < 3; i++) {
            int x1 = (int) (Math.random() * COVER_WIDTH);
            int y1 = (int) (Math.random() * COVER_HEIGHT);
            int x2 = (int) (Math.random() * COVER_WIDTH);
            int y2 = (int) (Math.random() * COVER_HEIGHT);
            g2d.drawLine(x1, y1, x2, y2);
        }
    }
    
    /**
     * 绘制书名
     */
    private static void drawTitle(Graphics2D g2d, String title, int width, int height) {
        if (title == null || title.isEmpty()) {
            return;
        }
        
        // 设置字体
        Font titleFont = new Font("Microsoft YaHei", Font.BOLD, 28);
        g2d.setFont(titleFont);
        g2d.setColor(Color.WHITE);
        
        // 计算文本位置（居中）
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(title);
        
        // 书名过长时换行
        int maxWidth = width - 40;
        if (textWidth > maxWidth) {
            // 分行显示
            String[] words = title.split("");
            StringBuilder line = new StringBuilder();
            int y = height / 2 - 30;
            
            for (String word : words) {
                String testLine = line.toString() + word;
                int testWidth = fm.stringWidth(testLine);
                
                if (testWidth > maxWidth && line.length() > 0) {
                    // 绘制当前行
                    int lineWidth = fm.stringWidth(line.toString());
                    g2d.drawString(line.toString(), (width - lineWidth) / 2, y);
                    line = new StringBuilder(word);
                    y += fm.getHeight() + 5;
                } else {
                    line.append(word);
                }
            }
            
            // 绘制最后一行
            if (line.length() > 0) {
                int lineWidth = fm.stringWidth(line.toString());
                g2d.drawString(line.toString(), (width - lineWidth) / 2, y);
            }
        } else {
            // 单行显示
            int x = (width - textWidth) / 2;
            int y = height / 2;
            g2d.drawString(title, x, y);
        }
    }
    
    /**
     * 绘制作者名
     */
    private static void drawAuthor(Graphics2D g2d, String author, int width, int height) {
        if (author == null || author.isEmpty()) {
            return;
        }
        
        // 设置字体（比书名小）
        Font authorFont = new Font("Microsoft YaHei", Font.PLAIN, 16);
        g2d.setFont(authorFont);
        g2d.setColor(new Color(255, 255, 255, 200)); // 半透明白色
        
        FontMetrics fm = g2d.getFontMetrics();
        String authorText = "—— " + author;
        int textWidth = fm.stringWidth(authorText);
        int x = (width - textWidth) / 2;
        int y = height / 2 + 60; // 书名下方
        
        g2d.drawString(authorText, x, y);
    }
    
    /**
     * 删除封面文件
     */
    public static boolean deleteCover(String coverPath) {
        if (coverPath == null || coverPath.isEmpty()) {
            return false;
        }
        
        try {
            Path path = Paths.get(coverPath);
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            System.err.println("删除封面失败: " + e.getMessage());
            return false;
        }
    }
}










