package com.library.server.service;

import com.library.common.protocol.ErrorCode;
import com.library.common.protocol.Request;
import com.library.common.protocol.Response;
import com.library.common.util.JsonUtil;
import com.library.server.dao.FineRateConfigDao;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 罚款梯度价格配置服务
 */
public class FineRateConfigService {
    private static final Logger logger = LoggerFactory.getLogger(FineRateConfigService.class);
    private final FineRateConfigDao configDao = new FineRateConfigDao();
    
    /**
     * 获取所有配置
     */
    public Response getAllConfigs(Request request) {
        String requestId = request.getRequestId();
        
        try {
            List<FineRateConfigDao.FineRateConfig> configs = configDao.findAll();
            
            ArrayNode configArray = JsonUtil.getObjectMapper().createArrayNode();
            for (FineRateConfigDao.FineRateConfig config : configs) {
                ObjectNode configNode = JsonUtil.createObjectNode();
                configNode.put("id", config.getId());
                configNode.put("dayRangeStart", config.getDayRangeStart());
                if (config.getDayRangeEnd() != null) {
                    configNode.put("dayRangeEnd", config.getDayRangeEnd());
                } else {
                    configNode.putNull("dayRangeEnd");
                }
                configNode.put("ratePerDay", config.getRatePerDay());
                configNode.put("description", config.getDescription() != null ? config.getDescription() : "");
                configNode.put("displayOrder", config.getDisplayOrder());
                configArray.add(configNode);
            }
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.set("configs", configArray);
            data.put("total", configs.size());
            
            return Response.success(requestId, "查询成功", JsonUtil.toJsonNode(data));
        } catch (Exception e) {
            logger.error("查询罚款梯度配置失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    /**
     * 更新配置
     */
    public Response updateConfig(Request request) {
        String requestId = request.getRequestId();
        
        try {
            Long id = request.getPayloadLong("id");
            Integer dayRangeStart = request.getPayloadInt("dayRangeStart");
            Integer dayRangeEnd = request.getPayloadInt("dayRangeEnd");
            Double ratePerDay = request.getPayloadDouble("ratePerDay");
            String description = request.getPayloadString("description");
            Integer displayOrder = request.getPayloadInt("displayOrder");
            
            if (id == null || dayRangeStart == null || ratePerDay == null || displayOrder == null) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    JsonUtil.toJsonNode("必填字段不能为空"));
            }
            
            if (dayRangeStart < 1) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    JsonUtil.toJsonNode("起始天数必须大于0"));
            }
            
            if (dayRangeEnd != null && dayRangeEnd < dayRangeStart) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    JsonUtil.toJsonNode("结束天数必须大于等于起始天数"));
            }
            
            if (ratePerDay < 0) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    JsonUtil.toJsonNode("每日费率不能为负数"));
            }
            
            FineRateConfigDao.FineRateConfig config = configDao.findById(id);
            if (config == null) {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                    JsonUtil.toJsonNode("配置不存在"));
            }
            
            // 检查唯一性约束
            boolean rangeChanged = !dayRangeStart.equals(config.getDayRangeStart()) ||
                    (dayRangeEnd == null && config.getDayRangeEnd() != null) ||
                    (dayRangeEnd != null && !dayRangeEnd.equals(config.getDayRangeEnd()));
            
            if (rangeChanged) {
                // 检查是否存在其他记录具有相同的(day_range_start, day_range_end)
                List<FineRateConfigDao.FineRateConfig> allConfigs = configDao.findAll();
                for (FineRateConfigDao.FineRateConfig existing : allConfigs) {
                    if (!existing.getId().equals(id)) { // 排除当前记录
                        boolean startMatch = existing.getDayRangeStart().equals(dayRangeStart);
                        boolean endMatch = (dayRangeEnd == null && existing.getDayRangeEnd() == null) ||
                                         (dayRangeEnd != null && existing.getDayRangeEnd() != null && 
                                          existing.getDayRangeEnd().equals(dayRangeEnd));
                        if (startMatch && endMatch) {
                            return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                                JsonUtil.toJsonNode("天数范围与其他配置冲突，请选择不同的范围"));
                        }
                    }
                }
            }
            
            config.setDayRangeStart(dayRangeStart);
            config.setDayRangeEnd(dayRangeEnd);
            config.setRatePerDay(ratePerDay);
            config.setDescription(description);
            config.setDisplayOrder(displayOrder);
            
            try {
                boolean updated = configDao.update(config);
                if (updated) {
                    logger.info("更新罚款梯度配置成功: id={}, dayRangeStart={}, dayRangeEnd={}, ratePerDay={}", 
                        id, dayRangeStart, dayRangeEnd, ratePerDay);
                    return Response.success(requestId, "更新成功", JsonUtil.toJsonNode("{}"));
                } else {
                    logger.warn("更新罚款梯度配置失败: id={}, 可能没有行被更新", id);
                    return Response.error(requestId, ErrorCode.SERVER_ERROR, 
                        JsonUtil.toJsonNode("更新失败：没有行被更新"));
                }
            } catch (RuntimeException e) {
                // 捕获数据库约束异常
                String errorMsg = e.getMessage();
                if (errorMsg != null && (errorMsg.contains("UNIQUE") || errorMsg.contains("unique") || 
                    errorMsg.contains("约束") || errorMsg.contains("冲突"))) {
                    logger.error("更新罚款梯度配置失败: 唯一性约束冲突, id={}", id, e);
                    return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                        JsonUtil.toJsonNode("天数范围与其他配置冲突，请选择不同的范围"));
                }
                throw e; // 重新抛出其他异常
            }
        } catch (Exception e) {
            logger.error("更新罚款梯度配置失败: requestId={}", requestId, e);
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("UNIQUE") || errorMsg.contains("unique") || 
                errorMsg.contains("约束") || errorMsg.contains("冲突"))) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    JsonUtil.toJsonNode("天数范围与其他配置冲突，请选择不同的范围"));
            }
            return Response.error(requestId, ErrorCode.SERVER_ERROR, 
                JsonUtil.toJsonNode("更新失败: " + (errorMsg != null ? errorMsg : e.getClass().getSimpleName())));
        }
    }
    
    /**
     * 添加配置
     */
    public Response addConfig(Request request) {
        String requestId = request.getRequestId();
        
        try {
            Integer dayRangeStart = request.getPayloadInt("dayRangeStart");
            Integer dayRangeEnd = request.getPayloadInt("dayRangeEnd");
            Double ratePerDay = request.getPayloadDouble("ratePerDay");
            String description = request.getPayloadString("description");
            Integer displayOrder = request.getPayloadInt("displayOrder");
            
            if (dayRangeStart == null || ratePerDay == null || displayOrder == null) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    JsonUtil.toJsonNode("必填字段不能为空"));
            }
            
            if (dayRangeStart < 1) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    JsonUtil.toJsonNode("起始天数必须大于0"));
            }
            
            if (dayRangeEnd != null && dayRangeEnd < dayRangeStart) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    JsonUtil.toJsonNode("结束天数必须大于等于起始天数"));
            }
            
            if (ratePerDay < 0) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    JsonUtil.toJsonNode("每日费率不能为负数"));
            }
            
            FineRateConfigDao.FineRateConfig config = new FineRateConfigDao.FineRateConfig();
            config.setDayRangeStart(dayRangeStart);
            config.setDayRangeEnd(dayRangeEnd);
            config.setRatePerDay(ratePerDay);
            config.setDescription(description);
            config.setDisplayOrder(displayOrder);
            
            Long id = configDao.insert(config);
            
            ObjectNode data = JsonUtil.createObjectNode();
            data.put("id", id);
            
            return Response.success(requestId, "添加成功", JsonUtil.toJsonNode(data));
        } catch (Exception e) {
            logger.error("添加罚款梯度配置失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
    
    /**
     * 删除配置
     */
    public Response deleteConfig(Request request) {
        String requestId = request.getRequestId();
        
        try {
            Long id = request.getPayloadLong("id");
            if (id == null) {
                return Response.error(requestId, ErrorCode.VALIDATION_ERROR, 
                    JsonUtil.toJsonNode("配置ID不能为空"));
            }
            
            boolean deleted = configDao.delete(id);
            if (deleted) {
                return Response.success(requestId, "删除成功", JsonUtil.toJsonNode("{}"));
            } else {
                return Response.error(requestId, ErrorCode.NOT_FOUND, 
                    JsonUtil.toJsonNode("配置不存在"));
            }
        } catch (Exception e) {
            logger.error("删除罚款梯度配置失败", e);
            return Response.error(requestId, ErrorCode.SERVER_ERROR);
        }
    }
}










