package com.agent.platform.dao.mapper.datastore;

import com.agent.platform.dao.entity.datastore.DataTable;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 自定义数据表 Mapper
 */
@Mapper
public interface DataTableMapper extends BaseMapper<DataTable> {

    /** 软删除：移入回收站并记录删除时间 */
    @Update("UPDATE data_table SET deleted = 1, deleted_time = NOW() WHERE id = #{id} AND deleted = 0")
    int markDeleted(@Param("id") Long id);
}
