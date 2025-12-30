package com.library.server.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 生成EXCEL示例导入数据文件
 */
public class ExcelSampleGenerator {
    public static void main(String[] args) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("图书数据");
            
            // 创建表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            
            // 创建表头
            Row headerRow = sheet.createRow(0);
            String[] headers = {"isbn", "title", "author", "category", "publisher", "description", "totalCount"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // 添加示例数据
            String[][] data = {
                {"978-7-111-12345-1", "Java编程思想", "Bruce Eckel", "计算机", "机械工业出版社", "Java编程经典教材，深入浅出讲解Java语言特性和面向对象编程思想", "10"},
                {"978-7-115-23456-2", "Effective Java", "Joshua Bloch", "计算机", "人民邮电出版社", "Java最佳实践指南，包含78条实用的编程建议", "8"},
                {"978-7-121-34567-3", "深入理解Java虚拟机", "周志明", "计算机", "电子工业出版社", "深入讲解JVM原理，包括内存管理、垃圾回收、类加载机制等", "12"},
                {"978-7-302-45678-4", "设计模式：可复用面向对象软件的基础", "GoF", "计算机", "清华大学出版社", "23种经典设计模式的详细讲解，面向对象设计的经典之作", "15"},
                {"978-7-111-56789-5", "算法导论", "Thomas H. Cormen", "计算机", "机械工业出版社", "算法和数据结构领域的权威教材，涵盖各种经典算法", "20"},
                {"978-7-115-67890-6", "Python编程：从入门到实践", "Eric Matthes", "计算机", "人民邮电出版社", "Python编程入门教材，通过实际项目学习编程", "18"},
                {"978-7-121-78901-7", "机器学习", "周志华", "计算机", "清华大学出版社", "机器学习领域的经典教材，系统介绍各种机器学习算法", "10"},
                {"978-7-302-89012-8", "深度学习", "Ian Goodfellow", "计算机", "人民邮电出版社", "深度学习领域的权威教材，深入讲解神经网络原理", "8"},
                {"978-7-111-90123-9", "操作系统概念", "Abraham Silberschatz", "计算机", "高等教育出版社", "操作系统经典教材，讲解进程管理、内存管理、文件系统等", "12"},
                {"978-7-115-01234-0", "计算机网络", "谢希仁", "计算机", "电子工业出版社", "计算机网络原理教材，涵盖物理层到应用层的完整内容", "15"}
            };
            
            // 填充数据
            for (int i = 0; i < data.length; i++) {
                Row row = sheet.createRow(i + 1);
                for (int j = 0; j < data[i].length; j++) {
                    Cell cell = row.createCell(j);
                    if (j == 6) { // totalCount列是数字
                        cell.setCellValue(Integer.parseInt(data[i][j]));
                    } else {
                        cell.setCellValue(data[i][j]);
                    }
                }
            }
            
            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            // 保存文件到项目根目录（从server目录回到根目录）
            String currentDir = System.getProperty("user.dir");
            String outputPath;
            if (currentDir.endsWith("server")) {
                outputPath = currentDir + "/../示例导入数据_EXCEL.xlsx";
            } else {
                outputPath = currentDir + "/示例导入数据_EXCEL.xlsx";
            }
            try (FileOutputStream outputStream = new FileOutputStream(outputPath)) {
                workbook.write(outputStream);
                System.out.println("EXCEL文件生成成功：" + outputPath);
            }
        } catch (IOException e) {
            System.err.println("生成EXCEL文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

