package com.agent.platform.dao.entity.asset;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 素材文件：元数据入库，二进制落盘于 platform.upload-dir 配置目录
 */
@Data
@TableName("asset_file")
public class AssetFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 素材名称（展示用） */
    private String name;

    /** 原始文件名 */
    private String originalName;

    /** 扩展名（小写不带点） */
    private String ext;

    /** MIME 类型 */
    private String contentType;

    /** 文件大小（字节） */
    private Long size;

    /** 分类：image/document/audio/video/other */
    private String category;

    /** 存储相对路径（实际文件位于 upload-dir 下） */
    private String storagePath;

    /** 状态：0删除 1正常 */
    private Integer status;

    /** 上传用户ID */
    private Long createdBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
