package com.agent.platform.dao.mapper.datastore;

import com.agent.platform.dao.entity.datastore.DataTable;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 自定义数据表 Mapper
 */
@Mapper
public interface DataTableMapper extends BaseMapper<DataTable> {
}
