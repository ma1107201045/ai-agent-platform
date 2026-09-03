package com.agent.platform.dao.entity.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话变量：跨会话保存的键值上下文（global 全局 / session 指定会话）
 */
@Data
@TableName("mem_variable")
public class MemVariable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 应用ID */
    private Long appId;

    /** 作用域：global 全局（跨会话） / session 指定会话 */
    private String scope;

    /** 所属会话ID（scope=session 时使用，空 = 该应用全部会话） */
    private Long conversationId;

    /** 变量名（英文下划线） */
    private String name;

    /** 变量值 */
    private String value;

    /** 类型：string/number/boolean/json */
    private String valueType;

    private String remark;

    /** 状态：0禁用 1启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
