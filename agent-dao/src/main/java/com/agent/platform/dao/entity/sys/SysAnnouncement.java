package com.agent.platform.dao.entity.sys;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台公告
 */
@Data
@TableName("sys_announcement")
public class SysAnnouncement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 公告标题 */
    private String title;

    /** 公告内容（支持换行的纯文本） */
    private String content;

    /** 受众范围：all 全部用户 */
    private String scope;

    /** 状态：0草稿 1发布中 2已下线 */
    private Integer status;

    /** 是否置顶：0否 1是 */
    private Integer pinned;

    /** 发布时间 */
    private LocalDateTime publishTime;

    /** 下线时间 */
    private LocalDateTime offlineTime;

    /** 发布人用户ID */
    private Long publisher;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0正常 1回收站 */
    @TableLogic
    private Integer deleted;

    /** 删除时间 */
    private LocalDateTime deletedTime;
}
