-- ============================================================
-- ai-agent-platform 数据库脚本（幂等，可重复执行）
-- 由 sql/agent_platform.sql（Navicat dump）拆分而来，2026-10-16
-- ============================================================
-- 本文件：种子数据（INSERT IGNORE）
-- 依赖主键/唯一键去重：已存在的记录跳过，不会清空或覆盖业务数据
-- 注意：无唯一键的表重复执行仍可能产生重复行，请按需节选执行
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Records of app_agent
-- ----------------------------
INSERT IGNORE INTO `app_agent` VALUES (11, 1, '多语言翻译助手', '中英互译与术语润色，支持行业术语定制', 'chatflow', '🌐', '你好，我可以帮你完成多语言翻译与润色。', NULL, 0, NULL, NULL, NULL, NULL, NULL, '2026-09-04 23:51:23', '2026-09-05 00:09:11', 1, '2026-09-05 00:09:11');
INSERT IGNORE INTO `app_agent` VALUES (12, 1, '111', '', 'agent', NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, '2026-09-05 00:04:23', '2026-09-05 00:09:09', 1, '2026-09-05 00:09:09');
INSERT IGNORE INTO `app_agent` VALUES (13, 1, '11', '1111', 'chatflow', NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, '2026-09-05 00:04:49', '2026-09-05 00:09:06', 1, '2026-09-05 00:09:06');
INSERT IGNORE INTO `app_agent` VALUES (14, 1, '2222', '', 'workflow', NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, '2026-09-05 00:05:02', '2026-09-05 00:09:07', 1, '2026-09-05 00:09:07');
INSERT IGNORE INTO `app_agent` VALUES (15, 1, '日报周报助手', '根据工作事项自动生成结构化日报/周报，支持按模板定制，提升汇报效率。', 'chatflow', NULL, '请描述今天完成的工作事项，我为您生成结构化日报。', '[\"生成今日日报\",\"生成本周周报\"]', 1, '{\"nodes\":[],\"edges\":[]}', NULL, NULL, NULL, 2, '2026-09-05 00:14:36', '2026-09-05 00:14:36', 0, NULL);
INSERT IGNORE INTO `app_agent` VALUES (16, 1, '日报周报助手', '根据工作事项自动生成结构化日报/周报，支持按模板定制，提升汇报效率。', 'chatflow', NULL, '请描述今天完成的工作事项，我为您生成结构化日报。', '[\"生成今日日报\",\"生成本周周报\"]', 1, '{\"nodes\":[{\"id\":\"node-1788690344322-1\",\"type\":\"start\",\"label\":\"开始\",\"position\":{\"x\":225,\"y\":166},\"config\":{\"variables\":[],\"welcome\":\"\"}},{\"id\":\"node-1788690344322-2\",\"type\":\"end\",\"label\":\"结束\",\"position\":{\"x\":730,\"y\":166},\"config\":{\"answerTemplate\":\"\"}},{\"id\":\"node-1788690349925-4\",\"type\":\"skill\",\"label\":\"技能执行\",\"position\":{\"x\":498,\"y\":-35},\"config\":{\"userPrompt\":\"{{input}}\",\"maxIterations\":6,\"includeSteps\":false,\"retries\":0,\"timeoutSeconds\":0,\"onError\":\"fail\",\"errorFallback\":\"\",\"outputVar\":\"\"}}],\"edges\":[{\"id\":\"edge-1788690349925-5\",\"source\":\"node-1788690349925-4\",\"target\":\"node-1788690344322-2\"},{\"id\":\"edge-1788690349925-6\",\"source\":\"node-1788690344322-1\",\"target\":\"node-1788690349925-4\"}]}', NULL, NULL, NULL, 3, '2026-09-06 18:25:37', '2026-09-06 18:25:54', 0, NULL);

-- ----------------------------
-- Records of app_agent_version
-- ----------------------------
INSERT IGNORE INTO `app_agent_version` VALUES (2, 15, 1, '{\"nodes\":[],\"edges\":[]}', NULL, 1, 1, '2026-09-05 00:14:36');
INSERT IGNORE INTO `app_agent_version` VALUES (3, 16, 1, '{\"nodes\":[],\"edges\":[]}', NULL, 1, 1, '2026-09-06 18:25:37');

-- ----------------------------
-- Records of app_api_key
-- ----------------------------
INSERT IGNORE INTO `app_api_key` VALUES (1, 1, 10, '111', 'sk-bf702d41f', '8102bf800ae2f992e2b19604d366f9344bedbdacdbc6cb176e2c968aea24a2c0', 1, NULL, NULL, 0, NULL, NULL, '2026-09-02 17:18:57', '2026-09-02 17:18:57');

-- ----------------------------
-- Records of app_market_item
-- ----------------------------
INSERT IGNORE INTO `app_market_item` VALUES (1, NULL, '智能客服助手', '基于知识库自动应答客户咨询，支持多轮追问与转人工引导，适合官网/电商客服场景。', 'customer_service', NULL, 'chatflow', '{\"nodes\":[],\"edges\":[]}', '{\"welcome_message\":\"您好，我是智能客服助手，请问有什么可以帮您？\",\"opening_questions\":[\"退货政策是什么？\",\"如何联系人工客服？\"]}', NULL, '平台官方', 0, 1, '2026-09-04 23:48:47', '2026-09-04 23:48:47');
INSERT IGNORE INTO `app_market_item` VALUES (2, NULL, '中英互译助手', '高质量中英互译与术语润色，支持行业术语定制，适合翻译与国际化业务。', 'translate', NULL, 'workflow', '{\"nodes\":[],\"edges\":[]}', '{\"welcome_message\":\"我可以帮您完成中英文互译与润色，请输入需要翻译的内容。\",\"opening_questions\":[\"帮我翻译这段话\",\"润色这段英文\"]}', NULL, '平台官方', 0, 1, '2026-09-04 23:48:47', '2026-09-04 23:48:47');
INSERT IGNORE INTO `app_market_item` VALUES (3, NULL, '日报周报助手', '根据工作事项自动生成结构化日报/周报，支持按模板定制，提升汇报效率。', 'office', NULL, 'chatflow', '{\"nodes\":[],\"edges\":[]}', '{\"welcome_message\":\"请描述今天完成的工作事项，我为您生成结构化日报。\",\"opening_questions\":[\"生成今日日报\",\"生成本周周报\"]}', NULL, '平台官方', 2, 1, '2026-09-04 23:48:47', '2026-09-06 18:25:36');

-- ----------------------------
-- Records of app_prompt_template
-- ----------------------------
INSERT IGNORE INTO `app_prompt_template` VALUES (1, 1, '通用结构化助手', '为任意任务设定专业助手的系统提示词模板', 'general', '你是一个专业的智能助手，名叫{{name}}。你的任务目标是：{{task}}。请严格围绕目标展开，若信息不足请向用户澄清而不是臆测。回答要求：1. 结论先行，逻辑清晰；2. 使用用户使用的语言；3. 涉及数据或结论时给出依据。请开始。', '[{\"name\":\"name\",\"desc\":\"助手名称\"},{\"name\":\"task\",\"desc\":\"任务目标\"}]', 1, 1, '2026-09-01 12:00:00', '2026-09-01 12:00:00');
INSERT IGNORE INTO `app_prompt_template` VALUES (2, 1, '资深专家角色扮演', '让模型扮演指定领域的资深专家（系统内置）', 'system', '你是一位拥有{{years}}年经验的资深{{expertise}}专家。请以该角色身份回答：1. 先给出明确结论，再分条阐述依据；2. 使用准确的行业术语，必要时作通俗解释；3. 超出专业范围时坦诚说明，并给出可靠的获取建议。回答语言请与用户保持一致。', '[{\"name\":\"years\",\"desc\":\"从业年限\"},{\"name\":\"expertise\",\"desc\":\"专业领域\"}]', 1, 1, '2026-09-01 12:00:00', '2026-09-01 12:00:00');
INSERT IGNORE INTO `app_prompt_template` VALUES (3, 1, '智能客服应答', '基于知识库内容的在线客服应答模板', 'business', '你是{{brand}}的在线客服{{serviceName}}。请基于给定的知识内容回答：{{knowledge}}。回答原则：1. 优先引用知识库，不编造事实；2. 无法解决时引导用户留下联系方式并承诺转人工跟进；3. 语气礼貌简洁。若用户情绪激动，请先安抚情绪再处理问题。', '[{\"name\":\"brand\",\"desc\":\"品牌名称\"},{\"name\":\"serviceName\",\"desc\":\"客服代号\"},{\"name\":\"knowledge\",\"desc\":\"知识库内容\"}]', 1, 1, '2026-09-01 12:00:00', '2026-09-01 12:00:00');
INSERT IGNORE INTO `app_prompt_template` VALUES (4, 1, '翻译润色助手', '跨语言翻译与润色的模板示例', 'custom', '你是资深翻译与润色专家。请将下方内容从{{srcLang}}翻译为{{tgtLang}}：{{input}}。要求：1. 准确传达原意与语气；2. 译文自然流畅，符合目标语言习惯；3. 保留专有名词、数字与格式；4. 如存在多义，选择最佳译法并附一句简短说明。', '[{\"name\":\"srcLang\",\"desc\":\"源语言\"},{\"name\":\"tgtLang\",\"desc\":\"目标语言\"},{\"name\":\"input\",\"desc\":\"待翻译内容\"}]', 1, 1, '2026-09-01 12:00:00', '2026-09-01 12:00:00');

-- ----------------------------
-- Records of app_prompt_version
-- ----------------------------
INSERT IGNORE INTO `app_prompt_version` VALUES (1, 1, 1, '你是一个专业的智能助手，名叫{{name}}。你的任务目标是：{{task}}。请严格围绕目标展开，若信息不足请向用户澄清而不是臆测。回答要求：1. 结论先行，逻辑清晰；2. 使用用户使用的语言；3. 涉及数据或结论时给出依据。请开始。', '[{\"name\":\"name\",\"desc\":\"助手名称\"},{\"name\":\"task\",\"desc\":\"任务目标\"}]', '初始版本', 1, '2026-09-01 12:00:00');
INSERT IGNORE INTO `app_prompt_version` VALUES (2, 2, 1, '你是一位拥有{{years}}年经验的资深{{expertise}}专家。请以该角色身份回答：1. 先给出明确结论，再分条阐述依据；2. 使用准确的行业术语，必要时作通俗解释；3. 超出专业范围时坦诚说明，并给出可靠的获取建议。回答语言请与用户保持一致。', '[{\"name\":\"years\",\"desc\":\"从业年限\"},{\"name\":\"expertise\",\"desc\":\"专业领域\"}]', '初始版本', 1, '2026-09-01 12:00:00');
INSERT IGNORE INTO `app_prompt_version` VALUES (3, 3, 1, '你是{{brand}}的在线客服{{serviceName}}。请基于给定的知识内容回答：{{knowledge}}。回答原则：1. 优先引用知识库，不编造事实；2. 无法解决时引导用户留下联系方式并承诺转人工跟进；3. 语气礼貌简洁。若用户情绪激动，请先安抚情绪再处理问题。', '[{\"name\":\"brand\",\"desc\":\"品牌名称\"},{\"name\":\"serviceName\",\"desc\":\"客服代号\"},{\"name\":\"knowledge\",\"desc\":\"知识库内容\"}]', '初始版本', 1, '2026-09-01 12:00:00');
INSERT IGNORE INTO `app_prompt_version` VALUES (4, 4, 1, '你是资深翻译与润色专家。请将下方内容从{{srcLang}}翻译为{{tgtLang}}：{{input}}。要求：1. 准确传达原意与语气；2. 译文自然流畅，符合目标语言习惯；3. 保留专有名词、数字与格式；4. 如存在多义，选择最佳译法并附一句简短说明。', '[{\"name\":\"srcLang\",\"desc\":\"源语言\"},{\"name\":\"tgtLang\",\"desc\":\"目标语言\"},{\"name\":\"input\",\"desc\":\"待翻译内容\"}]', '初始版本', 1, '2026-09-01 12:00:00');

-- ----------------------------
-- Records of app_skill
-- ----------------------------
INSERT IGNORE INTO `app_skill` VALUES (1, 1, '专家顾问应答', 'expert-advisor', '以资深领域顾问口吻，先给结论再分层论证，适用于专业咨询类问答。', 'system', 'instruction', NULL, '# 专家顾问应答技能\n\n当你需要以资深专家身份解答问题时，请遵循：\n\n1. 先给出明确结论（开头 1-2 句），再分条阐述依据；\n2. 使用准确的行业术语，必要时用通俗语言补充解释；\n3. 区分「事实」与「推测」，推测必须显式标注；\n4. 信息不足时先向用户澄清，不要臆测；\n5. 回答语言与用户保持一致。', '[]', NULL, NULL, 1, 1, 1, '2026-09-05 00:07:32', '2026-09-05 00:07:32');
INSERT IGNORE INTO `app_skill` VALUES (2, 1, '数据统计汇报', 'report-assistant', '基于当前时间与文本统计工具，输出结构化数据汇报。适用于数据统计、指标换算类任务。', 'system', 'tool', NULL, '# 数据统计汇报技能\n\n当你被要求统计、换算或汇报数据时，请按以下步骤执行：\n\n1. 先调用「获取当前时间」确认基准时间；\n2. 对用户给出的文本调用「统计文本」获取字符数/单词数/行数；\n3. 以「指标 - 数值 - 口径」表格形式汇报，口径需注明；\n4. 数值来源为工具结果，不得虚构。', '[]', '[1,2]', NULL, 1, 1, 1, '2026-09-05 00:07:32', '2026-09-05 00:07:32');
INSERT IGNORE INTO `app_skill` VALUES (3, 1, '合规客服应答', 'compliant-cs', '基于企业知识库的合规应答，话术严谨，引用知识来源，适合客服/售后场景。', 'system', 'knowledge', NULL, '# 合规客服应答技能\n\n当用户询问客服/售后/政策类问题时：\n\n1. 仅依据知识库资料作答，禁止编造条款；\n2. 每项答复尽量标注知识库依据编号；\n3. 资料未覆盖时引导留下联系方式并承诺转人工；\n4. 措辞礼貌、克制，不做过度承诺。', '[]', NULL, '[1]', 1, 1, 1, '2026-09-05 00:07:32', '2026-09-05 00:07:32');

-- ----------------------------
-- Records of app_skill_version
-- ----------------------------
INSERT IGNORE INTO `app_skill_version` VALUES (1, 1, 1, '# 专家顾问应答技能\n\n当你需要以资深专家身份解答问题时，请遵循：\n\n1. 先给出明确结论（开头 1-2 句），再分条阐述依据；\n2. 使用准确的行业术语，必要时用通俗语言补充解释；\n3. 区分「事实」与「推测」，推测必须显式标注；\n4. 信息不足时先向用户澄清，不要臆测；\n5. 回答语言与用户保持一致。', '[]', NULL, NULL, '初始版本', 1, '2026-09-05 00:07:32');
INSERT IGNORE INTO `app_skill_version` VALUES (2, 2, 1, '# 数据统计汇报技能\n\n当你被要求统计、换算或汇报数据时，请按以下步骤执行：\n\n1. 先调用「获取当前时间」确认基准时间；\n2. 对用户给出的文本调用「统计文本」获取字符数/单词数/行数；\n3. 以「指标 - 数值 - 口径」表格形式汇报，口径需注明；\n4. 数值来源为工具结果，不得虚构。', '[]', '[1,2]', NULL, '初始版本', 1, '2026-09-05 00:07:32');
INSERT IGNORE INTO `app_skill_version` VALUES (3, 3, 1, '# 合规客服应答技能\n\n当用户询问客服/售后/政策类问题时：\n\n1. 仅依据知识库资料作答，禁止编造条款；\n2. 每项答复尽量标注知识库依据编号；\n3. 资料未覆盖时引导留下联系方式并承诺转人工；\n4. 措辞礼貌、克制，不做过度承诺。', '[]', NULL, '[1]', '初始版本', 1, '2026-09-05 00:07:32');

-- ----------------------------
-- Records of app_template
-- ----------------------------
INSERT IGNORE INTO `app_template` VALUES (1, 0, '智能客服助手', 'customer-service', 'chatflow', '💬', '基于知识库回答客户问题，支持多轮追问与转人工', '电商 / 企业服务', '你好，我是智能客服助手，请问有什么可以帮您？', 1, 3, 1, NULL, '2026-09-01 10:00:00', '2026-09-01 10:00:00', 0, NULL);
INSERT IGNORE INTO `app_template` VALUES (2, 0, '多语言翻译助手', 'translate', 'chatflow', '🌐', '中英互译与术语润色，支持行业术语定制', '出海业务', '你好，我可以帮你完成多语言翻译与润色。', 1, 2, 1, NULL, '2026-09-01 10:00:00', '2026-09-04 23:51:23', 0, NULL);
INSERT IGNORE INTO `app_template` VALUES (3, 0, '内容创作助手', 'content', 'agent', '✍️', '撰写文章 / 文案 / 脚本，自动调用写作工具', '新媒体运营', '我来帮你创作内容，告诉我主题与风格即可。', 1, 0, 1, NULL, '2026-09-01 10:00:00', '2026-09-01 10:00:00', 0, NULL);
INSERT IGNORE INTO `app_template` VALUES (4, 0, '数据分析工作流', 'data-analysis', 'workflow', '📊', '数据接入-清洗-分析-报告一键生成', '经营分析', '', 1, 0, 1, NULL, '2026-09-01 10:00:00', '2026-09-01 10:00:00', 0, NULL);
INSERT IGNORE INTO `app_template` VALUES (5, 0, '营销文案助手', 'marketing', 'chatflow', '📣', '生成营销文案与活动创意，可切换文案风格', '市场运营', '我可以帮你生成各平台营销文案。', 1, 2, 1, NULL, '2026-09-01 10:00:00', '2026-09-01 10:00:00', 0, NULL);
INSERT IGNORE INTO `app_template` VALUES (6, 0, '编程助手', 'coding', 'agent', '👨‍💻', '代码生成 / 解释 / 重构，调用代码工具链', '研发提效', '我是编程助手，可以帮你编写与解释代码。', 1, 0, 1, NULL, '2026-09-01 10:00:00', '2026-09-01 10:00:00', 0, NULL);

-- ----------------------------
-- Records of app_tool
-- ----------------------------
INSERT IGNORE INTO `app_tool` VALUES (1, 1, 'get_current_time', '获取当前日期时间字符串', 'code', NULL, 'GET', NULL, 'none', NULL, '{\"type\":\"object\",\"properties\":{}}', 'return new java.text.SimpleDateFormat(\"yyyy-MM-dd HH:mm:ss\").format(new java.util.Date())', 1, '2026-08-28 00:22:58', '2026-08-28 00:22:58');
INSERT IGNORE INTO `app_tool` VALUES (2, 1, 'text_stats', '统计文本的长度、单词数和行数', 'code', NULL, 'GET', NULL, 'none', NULL, '{\"type\":\"object\",\"properties\":{}}', 'var t = input != null ? String.valueOf(input) : \"\"; return \"字符数=\" + t.length() + \", 单词数=\" + (t.trim().isEmpty() ? 0 : t.trim().split(\"\\\\s+\").length) + \", 行数=\" + t.split(\"\\n\").length', 1, '2026-08-28 00:22:58', '2026-08-28 00:22:58');

-- ----------------------------
-- Records of chat_agent_run
-- ----------------------------
INSERT IGNORE INTO `chat_agent_run` VALUES (1, 'run_ac85656d37be41a2', 9, NULL, 'workflow', '如何创建一个智能体应用？', '', 'success', NULL, '[]', 5, '2026-09-03 23:00:28', '2026-09-03 23:00:28');

-- ----------------------------
-- Records of chat_conversation
-- ----------------------------
INSERT IGNORE INTO `chat_conversation` VALUES (5, 1, 1, 15, '生成今日日报', 'direct', 5, 1, '2026-09-06 18:24:22', '2026-09-06 18:24:42');

-- ----------------------------
-- Records of chat_message
-- ----------------------------
INSERT IGNORE INTO `chat_message` VALUES (15, 5, 'user', '生成今日日报', NULL, 0, 1, '2026-09-06 18:24:22');
INSERT IGNORE INTO `chat_message` VALUES (16, 5, 'assistant', '下面给你一份可直接复制使用的**今日日报模板**。如果你把今天的具体工作内容发我，我可以帮你生成更正式的版本。\n\n---\n\n## 今日日报\n\n**姓名：**  \n**部门/岗位：**  \n**日期：** 今日\n\n### 一、今日工作完成情况\n1. 完成任务/事项：  \n   - 内容描述：  \n   - 进展状态：已完成 / 进行中 / 待跟进  \n   - 结果或产出：\n\n2. 完成任务/事项：  \n   - 内容描述：  \n   - 进展状态：已完成 / 进行中 / 待跟进  \n   - 结果或产出：\n\n3. 其他工作：  \n   - 会议、沟通、审批、文档、协作等事项：\n\n### 二、存在问题/风险\n1. 问题描述：  \n   - 影响：  \n   - 当前处理方式：  \n   - 是否需要支持：是 / 否\n\n2. 风险描述：  \n   - 可能影响：  \n   - 计划应对措施：\n\n### 三、明日工作计划\n1. 计划事项：  \n   - 目标：  \n   - 预计完成时间：\n\n2. 计划事项：  \n   - 目标：  \n   - 预计完成时间：\n\n### 四、需要协调/支持事项\n1. 需要支持事项：  \n   - 需要支持人/部门：  \n   - 希望支持内容：\n\n2. 其他说明：\n\n---\n\n## 简化版今日日报\n\n**今日完成：**  \n1. 完成了 XX 工作，目前状态为 XX。  \n2. 推进了 XX 事项，已解决/待解决 XX 问题。  \n3. 参与 XX 会议，确认了 XX 事项。\n\n**问题与风险：**  \n1. XX 事项存在 XX 风险，正在跟进处理。  \n2. 暂无其他重大问题。\n\n**明日计划：**  \n1. 继续推进 XX 工作，预计完成 XX。  \n2. 跟进 XX 问题，明确下一步处理方式。  \n3. 完成 XX 文档/沟通/审批事项。\n\n---\n\n如果你希望我直接生成你的正式日报，请补充以下信息：\n\n1. 你的岗位/部门  \n2. 今天做了哪些工作  \n3. 当前进度如何  \n4. 有没有遇到问题或风险  \n5. 明天计划做什么  \n6. 需要领导或同事支持什么', NULL, 0, 1, '2026-09-06 18:24:42');

-- ----------------------------
-- Records of chat_usage
-- ----------------------------
INSERT IGNORE INTO `chat_usage` VALUES (24, 1, 15, 5, 1, 5, 'console', 'direct', 0, 0, 0, 0.000000, '2026-09-06 18:24:42');

-- ----------------------------
-- Records of model_info
-- ----------------------------
INSERT IGNORE INTO `model_info` VALUES (5, 2, 'qwen3.8-27b', 'llm', NULL, NULL, '[\"stream\"]', 1, '2026-09-03 17:34:31', '2026-09-03 17:34:31');
INSERT IGNORE INTO `model_info` VALUES (6, 2, 'qwen3.7-text-embedding-flash', 'embedding', NULL, NULL, '[\"stream\",\"json_mode\",\"reasoning\"]', 1, '2026-09-03 17:37:18', '2026-09-03 22:51:52');
INSERT IGNORE INTO `model_info` VALUES (8, 3, 'doubao-seed-2-1-pro-260628', 'llm', NULL, NULL, '[\"vision\",\"stream\"]', 1, '2026-09-04 17:16:31', '2026-09-04 17:32:11');

-- ----------------------------
-- Records of model_provider
-- ----------------------------
INSERT IGNORE INTO `model_provider` VALUES (2, 1, 'DashScope', 'openai-compatible', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'sk-34cd7fe56f554bd7a79b509b6310434b', 1, '2026-09-03 17:33:45', '2026-09-03 17:47:03');
INSERT IGNORE INTO `model_provider` VALUES (3, 1, '火山方舟', 'openai-compatible', 'https://ark.cn-beijing.volces.com/api/v3', 'ark-0ff6dcc7-a492-4c59-9f2a-b75795e788c0-25e3e', 1, '2026-09-04 17:16:16', '2026-09-04 17:26:56');

-- ----------------------------
-- Records of ops_alert_event
-- ----------------------------
INSERT IGNORE INTO `ops_alert_event` VALUES (1, 1, 1, '错误率过高预警', 'error_rate', 'warning', '【手动测试】规则「错误率过高预警」触发：错误率 >= 5（60 分钟窗口）', 'handled', 'manual', '2026-09-03 15:24:00', '2026-09-03 15:30:12');
INSERT IGNORE INTO `ops_alert_event` VALUES (2, 1, 2, '运行失败数突增', 'failures', 'critical', '【手动测试】规则「运行失败数突增」触发：运行失败数 >= 10（60 分钟窗口）', 'open', 'manual', '2026-09-04 09:20:00', NULL);
INSERT IGNORE INTO `ops_alert_event` VALUES (3, 1, 3, '月成本超预算', 'cost', 'warning', '【手动测试】规则「月成本超预算」触发：成本(元) >= 300（1440 分钟窗口）', 'open', 'manual', '2026-09-05 00:01:45', NULL);
INSERT IGNORE INTO `ops_alert_event` VALUES (4, 1, 1, '错误率过高预警', 'error_rate', 'warning', '【手动测试】规则「错误率过高预警」触发：错误率 >= 5（60 分钟窗口）', 'open', 'manual', '2026-09-05 00:01:49', NULL);
INSERT IGNORE INTO `ops_alert_event` VALUES (5, 1, 2, '运行失败数突增', 'failures', 'critical', '【手动测试】规则「运行失败数突增」触发：运行失败数 >= 10（60 分钟窗口）', 'open', 'manual', '2026-09-05 00:01:49', NULL);
INSERT IGNORE INTO `ops_alert_event` VALUES (6, 1, 1, '错误率过高预警', 'error_rate', 'warning', '【手动测试】规则「错误率过高预警」触发：错误率 >= 5（60 分钟窗口）', 'open', 'manual', '2026-09-05 00:01:52', NULL);
INSERT IGNORE INTO `ops_alert_event` VALUES (7, 1, 1, '错误率过高预警', 'error_rate', 'warning', '【手动测试】规则「错误率过高预警」触发：错误率 >= 5（60 分钟窗口）', 'open', 'manual', '2026-09-05 00:01:52', NULL);
INSERT IGNORE INTO `ops_alert_event` VALUES (8, 1, 1, '错误率过高预警', 'error_rate', 'warning', '【手动测试】规则「错误率过高预警」触发：错误率 >= 5（60 分钟窗口）', 'open', 'manual', '2026-09-05 00:01:53', NULL);
INSERT IGNORE INTO `ops_alert_event` VALUES (9, 1, 1, '错误率过高预警', 'error_rate', 'warning', '【手动测试】规则「错误率过高预警」触发：错误率 >= 5（60 分钟窗口）', 'open', 'manual', '2026-09-05 00:01:53', NULL);
INSERT IGNORE INTO `ops_alert_event` VALUES (10, 1, 1, '错误率过高预警', 'error_rate', 'warning', '【手动测试】规则「错误率过高预警」触发：错误率 >= 5（60 分钟窗口）', 'open', 'manual', '2026-09-05 00:01:53', NULL);
INSERT IGNORE INTO `ops_alert_event` VALUES (11, 1, 1, '错误率过高预警', 'error_rate', 'warning', '【手动测试】规则「错误率过高预警」触发：错误率 >= 5（60 分钟窗口）', 'open', 'manual', '2026-09-05 00:01:53', NULL);
INSERT IGNORE INTO `ops_alert_event` VALUES (12, 1, 1, '错误率过高预警', 'error_rate', 'warning', '【手动测试】规则「错误率过高预警」触发：错误率 >= 5（60 分钟窗口）', 'handled', 'manual', '2026-09-05 00:01:53', '2026-09-05 00:02:00');

-- ----------------------------
-- Records of ops_alert_rule
-- ----------------------------
INSERT IGNORE INTO `ops_alert_rule` VALUES (1, 1, '错误率过高预警', 'error_rate', '>=', 5.0000, 60, 'warning', 'notification,webhook', 'https://example.com/hooks/agent-platform-alert', 1, '应用错误率超过 5% 时触发告警', '2026-09-05 00:01:53', '2026-09-02 10:00:00', '2026-09-05 00:01:53');
INSERT IGNORE INTO `ops_alert_rule` VALUES (2, 1, '运行失败数突增', 'failures', '>=', 10.0000, 60, 'critical', 'notification,email', NULL, 1, '60 分钟内运行失败超过 10 次', '2026-09-05 00:01:50', '2026-09-02 10:05:00', '2026-09-05 00:01:50');
INSERT IGNORE INTO `ops_alert_rule` VALUES (3, 1, '月成本超预算', 'cost', '>=', 300.0000, 1440, 'warning', 'notification', NULL, 1, '月度累计成本超过 300 元提醒', '2026-09-05 00:01:45', '2026-09-02 10:10:00', '2026-09-05 00:01:45');

-- ----------------------------
-- Records of sys_announcement
-- ----------------------------
INSERT IGNORE INTO `sys_announcement` VALUES (1, 1, 'AgentForge 平台 v1.2 功能升级公告', '各位用户：\n\n平台已于近期完成 v1.2 版本升级，本次升级新增以下能力：\n1. 新增费用账单与月度预算提醒；\n2. 新增发布渠道与应用市场模块；\n3. 优化多智能体编排与运行观测体验。\n\n如遇使用问题，可前往「帮助与文档」查看使用指南。', 'all', 1, 1, '2026-09-01 10:00:00', NULL, 1, '2026-09-01 10:00:00', '2026-09-01 10:00:00', 0, NULL);
INSERT IGNORE INTO `sys_announcement` VALUES (2, 1, '关于数据安全使用的提醒（草稿）', '请勿在知识库与提示词中上传含敏感信息的资料，注意遵守数据安全规范。', 'all', 0, 0, NULL, NULL, 1, '2026-09-02 14:20:00', '2026-09-02 14:20:00', 0, NULL);

-- ----------------------------
-- Records of sys_oper_log
-- ----------------------------
INSERT IGNORE INTO `sys_oper_log` VALUES (1, 1, 1, 'admin', '智能体', '发布应用', 'POST', '/api/app/agents/3/publish', '127.0.0.1', 1, NULL, 560, '2026-09-04 10:12:03');
INSERT IGNORE INTO `sys_oper_log` VALUES (2, 1, 1, 'admin', '知识库', '上传文档', 'POST', '/api/knowledge/documents/upload', '127.0.0.1', 1, NULL, 1240, '2026-09-04 10:05:41');
INSERT IGNORE INTO `sys_oper_log` VALUES (3, 1, 1, 'admin', '系统管理', '新增成员', 'POST', '/api/sys/users', '127.0.0.1', 1, NULL, 45, '2026-09-03 18:42:09');
INSERT IGNORE INTO `sys_oper_log` VALUES (4, 1, 1, 'admin', '工具', '配置数据源', 'PUT', '/api/tool/integrations/7', '127.0.0.1', 1, NULL, 88, '2026-09-03 16:20:55');
INSERT IGNORE INTO `sys_oper_log` VALUES (5, 1, 1, 'admin', '模型', '更新供应商', 'PUT', '/api/models/1', '127.0.0.1', 1, NULL, 63, '2026-09-03 11:08:30');
INSERT IGNORE INTO `sys_oper_log` VALUES (6, 1, 1, 'admin', '系统管理', '删除成员', 'DELETE', '/api/sys/users/9', '127.0.0.1', 0, '用户不存在: 9', 12, '2026-09-02 15:33:47');
INSERT IGNORE INTO `sys_oper_log` VALUES (7, 1, 1, 'admin', '提示词库', '新增提示词', 'POST', '/api/app/prompts', '127.0.0.1', 1, NULL, 28, '2026-09-02 09:14:26');
INSERT IGNORE INTO `sys_oper_log` VALUES (8, 1, 1, 'admin', '评测', '运行评测任务', 'POST', '/api/eval/tasks/2/run', '127.0.0.1', 1, NULL, 982, '2026-09-01 20:47:12');
INSERT IGNORE INTO `sys_oper_log` VALUES (9, 1, 1, 'admin', '智能体', '删除', 'DELETE', '/api/app/agents/9', '0:0:0:0:0:0:0:1', 1, NULL, 30, '2026-09-04 23:50:31');
INSERT IGNORE INTO `sys_oper_log` VALUES (10, 1, 1, 'admin', '智能体', '删除', 'DELETE', '/api/app/agents/8', '0:0:0:0:0:0:0:1', 1, NULL, 12, '2026-09-04 23:50:32');
INSERT IGNORE INTO `sys_oper_log` VALUES (11, 1, 1, 'admin', '智能体', '删除', 'DELETE', '/api/app/agents/6', '0:0:0:0:0:0:0:1', 1, NULL, 18, '2026-09-04 23:50:34');
INSERT IGNORE INTO `sys_oper_log` VALUES (12, 1, 1, 'admin', '应用模板', '从模板创建', 'POST', '/api/app/templates/2/instantiate', '0:0:0:0:0:0:0:1', 1, NULL, 31, '2026-09-04 23:51:23');
INSERT IGNORE INTO `sys_oper_log` VALUES (13, 1, 1, 'admin', '其他', '删除', 'POST', '/api/system/trash/purge', '0:0:0:0:0:0:0:1', 1, NULL, 59, '2026-09-04 23:59:41');
INSERT IGNORE INTO `sys_oper_log` VALUES (14, 1, 1, 'admin', '其他', '删除', 'POST', '/api/system/trash/purge', '0:0:0:0:0:0:0:1', 1, NULL, 27, '2026-09-04 23:59:43');
INSERT IGNORE INTO `sys_oper_log` VALUES (15, 1, 1, 'admin', '其他', '清理', 'POST', '/api/system/trash/cleanup', '0:0:0:0:0:0:0:1', 1, NULL, 12, '2026-09-04 23:59:44');
INSERT IGNORE INTO `sys_oper_log` VALUES (16, 1, 1, 'admin', '其他', '删除', 'POST', '/api/system/trash/purge', '0:0:0:0:0:0:0:1', 1, NULL, 33, '2026-09-04 23:59:46');
INSERT IGNORE INTO `sys_oper_log` VALUES (17, 1, 1, 'admin', '观测', '测试', 'POST', '/api/ops/alerts/events/test', '0:0:0:0:0:0:0:1', 1, NULL, 29, '2026-09-05 00:01:45');
INSERT IGNORE INTO `sys_oper_log` VALUES (18, 1, 1, 'admin', '观测', '测试', 'POST', '/api/ops/alerts/events/test', '0:0:0:0:0:0:0:1', 1, NULL, 18, '2026-09-05 00:01:49');
INSERT IGNORE INTO `sys_oper_log` VALUES (19, 1, 1, 'admin', '观测', '测试', 'POST', '/api/ops/alerts/events/test', '0:0:0:0:0:0:0:1', 1, NULL, 17, '2026-09-05 00:01:50');
INSERT IGNORE INTO `sys_oper_log` VALUES (20, 1, 1, 'admin', '观测', '测试', 'POST', '/api/ops/alerts/events/test', '0:0:0:0:0:0:0:1', 1, NULL, 20, '2026-09-05 00:01:52');
INSERT IGNORE INTO `sys_oper_log` VALUES (21, 1, 1, 'admin', '观测', '测试', 'POST', '/api/ops/alerts/events/test', '0:0:0:0:0:0:0:1', 1, NULL, 13, '2026-09-05 00:01:52');
INSERT IGNORE INTO `sys_oper_log` VALUES (22, 1, 1, 'admin', '观测', '测试', 'POST', '/api/ops/alerts/events/test', '0:0:0:0:0:0:0:1', 1, NULL, 22, '2026-09-05 00:01:53');
INSERT IGNORE INTO `sys_oper_log` VALUES (23, 1, 1, 'admin', '观测', '测试', 'POST', '/api/ops/alerts/events/test', '0:0:0:0:0:0:0:1', 1, NULL, 18, '2026-09-05 00:01:53');
INSERT IGNORE INTO `sys_oper_log` VALUES (24, 1, 1, 'admin', '观测', '测试', 'POST', '/api/ops/alerts/events/test', '0:0:0:0:0:0:0:1', 1, NULL, 19, '2026-09-05 00:01:53');
INSERT IGNORE INTO `sys_oper_log` VALUES (25, 1, 1, 'admin', '观测', '测试', 'POST', '/api/ops/alerts/events/test', '0:0:0:0:0:0:0:1', 1, NULL, 19, '2026-09-05 00:01:53');
INSERT IGNORE INTO `sys_oper_log` VALUES (26, 1, 1, 'admin', '观测', '测试', 'POST', '/api/ops/alerts/events/test', '0:0:0:0:0:0:0:1', 1, NULL, 16, '2026-09-05 00:01:53');
INSERT IGNORE INTO `sys_oper_log` VALUES (27, 1, 1, 'admin', '观测', '修改', 'PUT', '/api/ops/alerts/events/12/status', '0:0:0:0:0:0:0:1', 1, NULL, 13, '2026-09-05 00:02:00');
INSERT IGNORE INTO `sys_oper_log` VALUES (28, 1, 1, 'admin', '智能体', '新增', 'POST', '/api/app/agents', '0:0:0:0:0:0:0:1', 1, NULL, 24, '2026-09-05 00:04:23');
INSERT IGNORE INTO `sys_oper_log` VALUES (29, 1, 1, 'admin', '智能体', '新增', 'POST', '/api/app/agents', '0:0:0:0:0:0:0:1', 1, NULL, 15, '2026-09-05 00:04:49');
INSERT IGNORE INTO `sys_oper_log` VALUES (30, 1, 1, 'admin', '智能体', '新增', 'POST', '/api/app/agents', '0:0:0:0:0:0:0:1', 1, NULL, 8, '2026-09-05 00:05:02');
INSERT IGNORE INTO `sys_oper_log` VALUES (31, 1, 1, 'admin', '智能体', '删除', 'DELETE', '/api/app/agents/13', '0:0:0:0:0:0:0:1', 1, NULL, 33, '2026-09-05 00:09:06');
INSERT IGNORE INTO `sys_oper_log` VALUES (32, 1, 1, 'admin', '智能体', '删除', 'DELETE', '/api/app/agents/14', '0:0:0:0:0:0:0:1', 1, NULL, 20, '2026-09-05 00:09:08');
INSERT IGNORE INTO `sys_oper_log` VALUES (33, 1, 1, 'admin', '智能体', '删除', 'DELETE', '/api/app/agents/12', '0:0:0:0:0:0:0:1', 1, NULL, 22, '2026-09-05 00:09:10');
INSERT IGNORE INTO `sys_oper_log` VALUES (34, 1, 1, 'admin', '智能体', '删除', 'DELETE', '/api/app/agents/11', '0:0:0:0:0:0:0:1', 1, NULL, 22, '2026-09-05 00:09:11');
INSERT IGNORE INTO `sys_oper_log` VALUES (35, 1, 1, 'admin', '其他', '新增', 'POST', '/api/app-market/3/install', '0:0:0:0:0:0:0:1', 1, NULL, 1078, '2026-09-05 00:14:36');
INSERT IGNORE INTO `sys_oper_log` VALUES (36, 1, NULL, 'admin', '认证', '登录成功', 'POST', '/api/sys/auth/login', '0:0:0:0:0:0:0:1', 1, NULL, 174, '2026-09-06 18:22:42');
INSERT IGNORE INTO `sys_oper_log` VALUES (37, 1, NULL, 'admin', '认证', '登录成功', 'POST', '/api/sys/auth/login', '0:0:0:0:0:0:0:1', 1, NULL, 134, '2026-09-06 18:23:20');
INSERT IGNORE INTO `sys_oper_log` VALUES (38, 1, 1, 'admin', '模型', '新增', 'POST', '/api/model/models/chat-stream', '0:0:0:0:0:0:0:1', 1, NULL, 2, '2026-09-06 18:23:48');
INSERT IGNORE INTO `sys_oper_log` VALUES (39, 1, 1, 'admin', '对话记录', '新增', 'POST', '/api/chat/conversations', '0:0:0:0:0:0:0:1', 1, NULL, 48, '2026-09-06 18:24:22');
INSERT IGNORE INTO `sys_oper_log` VALUES (40, 1, 1, 'admin', '对话记录', '新增', 'POST', '/api/chat/conversations/5/messages/stream', '0:0:0:0:0:0:0:1', 1, NULL, 1, '2026-09-06 18:24:22');
INSERT IGNORE INTO `sys_oper_log` VALUES (41, 1, 1, 'admin', '其他', '新增', 'POST', '/api/app-market/3/install', '0:0:0:0:0:0:0:1', 1, NULL, 58, '2026-09-06 18:25:37');
INSERT IGNORE INTO `sys_oper_log` VALUES (42, 1, 1, 'admin', '智能体', '修改', 'PUT', '/api/app/agents/16', '0:0:0:0:0:0:0:1', 1, NULL, 14, '2026-09-06 18:25:54');
INSERT IGNORE INTO `sys_oper_log` VALUES (43, 1, 1, 'admin', '应用管理', '修改', 'PUT', '/api/app/skills/1', '0:0:0:0:0:0:0:1', 0, '技能名称不能为空', 10, '2026-09-06 18:34:22');
INSERT IGNORE INTO `sys_oper_log` VALUES (44, 1, 1, 'admin', '应用管理', '修改', 'PUT', '/api/app/skills/1', '0:0:0:0:0:0:0:1', 0, '技能名称不能为空', 9, '2026-09-06 18:43:24');
INSERT IGNORE INTO `sys_oper_log` VALUES (45, 1, NULL, 'admin', '认证', '登录成功', 'POST', '/api/sys/auth/login', '0:0:0:0:0:0:0:1', 1, NULL, 124, '2026-09-06 18:48:08');

-- ----------------------------
-- Records of sys_tenant
-- ----------------------------
INSERT IGNORE INTO `sys_tenant` VALUES (1, '默认租户', 'default', 'pro', 1, '2026-08-28 00:22:58', '2026-08-28 00:22:58');

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT IGNORE INTO `sys_user` VALUES (1, 1, 'admin', '$2a$10$9q7nqAmKWk7KcW/peXz.Ru4OVkaJUjrrI4UElU1RRk.Z.V6rSrDgq', '管理员', NULL, NULL, 1, '2026-08-28 00:22:58', '2026-08-28 01:23:10');

-- ----------------------------
-- Records of sys_user_security
-- ----------------------------
INSERT IGNORE INTO `sys_user_security` VALUES (1, 1, 1, NULL, 0, NULL, NULL, '2026-09-06 18:48:08', NULL, 3, '2026-09-06 18:22:42', '2026-09-06 18:48:08');

SET FOREIGN_KEY_CHECKS = 1;
