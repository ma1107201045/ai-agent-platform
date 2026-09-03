package com.agent.platform.dao.mapper.datastore;

import com.agent.platform.dao.entity.datastore.DataRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据记录 Mapper
 */
@Mapper
public interface DataRecordMapper extends BaseMapper<DataRecord> {
}
