package com.agent.platform.dao.mapper.sys;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 回收站查询与操作（面向已逻辑删除的数据，绕过逻辑删除过滤条件）
 */
@Mapper
public interface TrashMapper {

    /* ---------------- 列表 ---------------- */

    @Select("SELECT id, name AS name, deleted_time AS deleted_time FROM app_agent " +
            "WHERE deleted = 1 ORDER BY COALESCE(deleted_time, update_time) DESC")
    List<Map<String, Object>> listDeletedAgent();

    @Select("SELECT id, name AS name, deleted_time AS deleted_time FROM knowledge_dataset " +
            "WHERE deleted = 1 ORDER BY COALESCE(deleted_time, update_time) DESC")
    List<Map<String, Object>> listDeletedDataset();

    @Select("SELECT id, name AS name, deleted_time AS deleted_time FROM app_template " +
            "WHERE deleted = 1 ORDER BY COALESCE(deleted_time, update_time) DESC")
    List<Map<String, Object>> listDeletedTemplate();

    @Select("SELECT id, title AS name, deleted_time AS deleted_time FROM sys_announcement " +
            "WHERE deleted = 1 ORDER BY COALESCE(deleted_time, update_time) DESC")
    List<Map<String, Object>> listDeletedAnnouncement();

    /* ---------------- 恢复 ---------------- */

    @Update("UPDATE app_agent SET deleted = 0, deleted_time = NULL WHERE id = #{id}")
    int restoreAgent(@Param("id") Long id);

    @Update("UPDATE knowledge_dataset SET deleted = 0, deleted_time = NULL WHERE id = #{id}")
    int restoreDataset(@Param("id") Long id);

    @Update("UPDATE app_template SET deleted = 0, deleted_time = NULL WHERE id = #{id}")
    int restoreTemplate(@Param("id") Long id);

    @Update("UPDATE sys_announcement SET deleted = 0, deleted_time = NULL WHERE id = #{id}")
    int restoreAnnouncement(@Param("id") Long id);

    /* ---------------- 彻底删除（级联） ---------------- */

    @Delete("DELETE FROM app_agent WHERE id = #{id}")
    int purgeAgent(@Param("id") Long id);

    @Delete("DELETE FROM app_agent_version WHERE app_id = #{id}")
    int purgeAgentVersions(@Param("id") Long id);

    @Delete("DELETE FROM chat_usage WHERE app_id = #{id}")
    int purgeAgentUsage(@Param("id") Long id);

    @Delete("DELETE cm FROM chat_message cm INNER JOIN chat_conversation cc " +
            "ON cm.conversation_id = cc.id WHERE cc.app_id = #{id}")
    int purgeAgentMessages(@Param("id") Long id);

    @Delete("DELETE FROM chat_conversation WHERE app_id = #{id}")
    int purgeAgentConversations(@Param("id") Long id);

    @Delete("DELETE FROM knowledge_dataset WHERE id = #{id}")
    int purgeDataset(@Param("id") Long id);

    @Delete("DELETE FROM knowledge_chunk WHERE dataset_id = #{id}")
    int purgeDatasetChunks(@Param("id") Long id);

    @Delete("DELETE FROM knowledge_document WHERE dataset_id = #{id}")
    int purgeDatasetDocuments(@Param("id") Long id);

    @Delete("DELETE FROM app_template WHERE id = #{id}")
    int purgeTemplate(@Param("id") Long id);

    @Delete("DELETE FROM sys_announcement WHERE id = #{id}")
    int purgeAnnouncement(@Param("id") Long id);
}
