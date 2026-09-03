package com.agent.platform.dao.entity.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流运行记录（运行监控页数据源，由引擎事件持久化）
 */
@Data
@TableName("chat_agent_run")
public class ChatAgentRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 运行标识（引擎 runId，业务侧全局唯一） */
    private String runId;

    /** 应用ID */
    private Long appId;

    /** 会话ID（可空：公开调用 / 未关联会话时为空） */
    private Long conversationId;

    /** 运行模式: workflow / agent */
    private String mode;

    /** 用户输入（本次运行的 userInput） */
    private String input;

    /** 最终回答（对用户友好） */
    private String answer;

    /** 运行状态: running/success/failed/canceled/timeout（见引擎 RunStatus code） */
    private String status;

    /** 技术性错误描述（失败/超时时才有） */
    private String error;

    /** 节点执行轨迹（JSON 数组） */
    private String traceJson;

    /** 总耗时（毫秒） */
    private Long costMs;

    private LocalDateTime createTime;

    /** 结束时间 */
    private LocalDateTime finishTime;
}
