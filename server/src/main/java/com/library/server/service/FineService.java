package com.library.server.service;

import com.library.server.dao.FineRateConfigDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class FineService {
    private static final Logger logger = LoggerFactory.getLogger(FineService.class);
    private static final FineRateConfigDao configDao = new FineRateConfigDao();
    
    private static final double DEFAULT_FINE_RATE_1_7 = 1.0;
    private static final double DEFAULT_FINE_RATE_8_30 = 2.0;
    private static final double DEFAULT_FINE_RATE_31_PLUS = 5.0;
    
    public static double calculateFine(long overdueDays) {
        if (overdueDays <= 0) {
            return 0.0;
        }
        
        try {
            List<FineRateConfigDao.FineRateConfig> configs = configDao.findAll();
            if (configs.isEmpty()) {
                return calculateFineWithDefaults(overdueDays);
            }
            
            double totalFine = 0.0;
            long remainingDays = overdueDays;
            
            for (FineRateConfigDao.FineRateConfig config : configs) {
                int start = config.getDayRangeStart();
                Integer end = config.getDayRangeEnd();
                double rate = config.getRatePerDay();
                
                if (remainingDays <= 0) {
                    break;
                }
                
                if (end == null) {
                    totalFine += remainingDays * rate;
                    break;
                } else {
                    int rangeDays = end - start + 1;
                    if (remainingDays <= rangeDays) {
                        totalFine += remainingDays * rate;
                        break;
                    } else {
                        totalFine += rangeDays * rate;
                        remainingDays -= rangeDays;
                    }
                }
            }
            
            return totalFine;
        } catch (Exception e) {
            logger.error("计算罚款失败，使用默认配置", e);
            return calculateFineWithDefaults(overdueDays);
        }
    }
    
    private static double calculateFineWithDefaults(long overdueDays) {
        if (overdueDays <= 7) {
            return overdueDays * DEFAULT_FINE_RATE_1_7;
        } else if (overdueDays <= 30) {
            return 7 * DEFAULT_FINE_RATE_1_7 + (overdueDays - 7) * DEFAULT_FINE_RATE_8_30;
        } else {
            return 7 * DEFAULT_FINE_RATE_1_7 + 23 * DEFAULT_FINE_RATE_8_30 + 
                   (overdueDays - 30) * DEFAULT_FINE_RATE_31_PLUS;
        }
    }
    
    public static long calculateOverdueDays(LocalDateTime dueTime) {
        if (dueTime == null) {
            return 0;
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(dueTime)) {
            return 0;
        }
        
        return ChronoUnit.DAYS.between(dueTime, now);
    }
    
    public static String getFineDescription(long overdueDays) {
        if (overdueDays <= 0) {
            return "无逾期";
        }
        
        double fine = calculateFine(overdueDays);
        StringBuilder desc = new StringBuilder();
        desc.append("逾期").append(overdueDays).append("天，");
        
        try {
            List<FineRateConfigDao.FineRateConfig> configs = configDao.findAll();
            if (configs.isEmpty()) {
                desc.append("前7天每天").append(DEFAULT_FINE_RATE_1_7).append("元");
                if (overdueDays > 7) {
                    desc.append("，第8-30天每天").append(DEFAULT_FINE_RATE_8_30).append("元");
                }
                if (overdueDays > 30) {
                    desc.append("，31天以上每天").append(DEFAULT_FINE_RATE_31_PLUS).append("元");
                }
            } else {
                boolean first = true;
                for (FineRateConfigDao.FineRateConfig config : configs) {
                    int start = config.getDayRangeStart();
                    Integer end = config.getDayRangeEnd();
                    double rate = config.getRatePerDay();
                    
                    if (!first) {
                        desc.append("，");
                    }
                    first = false;
                    
                    if (end == null) {
                        desc.append("第").append(start).append("天以上每天").append(rate).append("元");
                    } else {
                        desc.append("第").append(start).append("-").append(end).append("天每天").append(rate).append("元");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("生成罚款描述失败", e);
            desc.append("按默认费率计算");
        }
        
        desc.append("，合计：").append(String.format("%.2f", fine)).append("元");
        return desc.toString();
    }
}











