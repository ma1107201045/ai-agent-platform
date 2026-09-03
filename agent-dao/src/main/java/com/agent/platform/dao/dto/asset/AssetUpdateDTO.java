package com.agent.platform.dao.dto.asset;

import lombok.Data;

/**
 * 更新素材元数据入参
 */
@Data
public class AssetUpdateDTO {
    private String name;
    private String category;
    private Integer status;
}
