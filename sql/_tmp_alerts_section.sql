
-- ----------------------------
-- Table structure for ops_alert_rule 告警规则
-- ----------------------------
DROP TABLE IF EXISTS `ops_alert_rule`;
CREATE TABLE `ops_alert_rule`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规则名称',
  `metric` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '指标: error_rate错误率/failures失败数/latency延迟/cost成本',
  `operator` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '>=' COMMENT '比较符',
  `threshold` decimal(12, 4) NULL DEFAULT NULL COMMENT '阈值',
  `window_minutes` int NULL DEFAULT 60 COMMENT '统计窗口(分钟)',
  `level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'warning' COMMENT '级别: warning/critical',
  `channels` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'notification' COMMENT '通知渠道: notification,email,webhook',
  `webhook_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'Webhook地址',
  `enabled` tinyint NULL DEFAULT 1 COMMENT '是否启用: 0否 1是',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `last_fire_time` datetime NULL DEFAULT NULL COMMENT '最近触发时间',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant`(`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '告警规则' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ops_alert_rule
-- ----------------------------
INSERT INTO `ops_alert_rule` VALUES (1, 1, '错误率过高预警', 'error_rate', '>=', 5.0000, 60, 'warning', 'notification,webhook', 'https://example.com/hooks/agent-platform-alert', 1, '应用错误率超过 5% 时触发告警', '2026-09-04 09:20:00', '2026-09-02 10:00:00', '2026-09-02 10:00:00');
INSERT INTO `ops_alert_rule` VALUES (2, 1, '运行失败数突增', 'failures', '>=', 10.0000, 60, 'critical', 'notification,email', NULL, 1, '60 分钟内运行失败超过 10 次', NULL, '2026-09-02 10:05:00', '2026-09-02 10:05:00');
INSERT INTO `ops_alert_rule` VALUES (3, 1, '月成本超预算', 'cost', '>=', 300.0000, 1440, 'warning', 'notification', NULL, 1, '月度累计成本超过 300 元提醒', NULL, '2026-09-02 10:10:00', '2026-09-02 10:10:00');

-- ----------------------------
-- Table structure for ops_alert_event 告警事件
-- ----------------------------
DROP TABLE IF EXISTS `ops_alert_event`;
CREATE TABLE `ops_alert_event`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `rule_id` bigint NULL DEFAULT NULL COMMENT '规则ID',
  `rule_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规则名(冗余)',
  `metric` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '指标',
  `level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'warning' COMMENT '级别',
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '触发描述',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'open' COMMENT '状态: open/handled/ignored',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'auto' COMMENT '来源: manual手动测试/auto自动',
  `trigger_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '触发时间',
  `handled_time` datetime NULL DEFAULT NULL COMMENT '处理时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_status`(`tenant_id`, `status`) USING BTREE,
  INDEX `idx_rule`(`rule_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '告警事件' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ops_alert_event
-- ----------------------------
INSERT INTO `ops_alert_event` VALUES (1, 1, 1, '错误率过高预警', 'error_rate', 'warning', '【手动测试】规则「错误率过高预警」触发：错误率 >= 5（60 分钟窗口）', 'handled', 'manual', '2026-09-03 15:24:00', '2026-09-03 15:30:12');
INSERT INTO `ops_alert_event` VALUES (2, 1, 2, '运行失败数突增', 'failures', 'critical', '【手动测试】规则「运行失败数突增」触发：运行失败数 >= 10（60 分钟窗口）', 'open', 'manual', '2026-09-04 09:20:00', NULL);
