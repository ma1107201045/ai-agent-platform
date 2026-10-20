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
            "WHERE deleted = 1 AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) ORDER BY COALESCE(deleted_time, update_time) DESC")
    List<Map<String, Object>> listDeletedAgent(@Param("tenantId") Long tenantId);

    @Select("SELECT id, name AS name, deleted_time AS deleted_time FROM knowledge_dataset " +
            "WHERE deleted = 1 AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) ORDER BY COALESCE(deleted_time, update_time) DESC")
    List<Map<String, Object>> listDeletedDataset(@Param("tenantId") Long tenantId);

    @Select("SELECT id, name AS name, deleted_time AS deleted_time FROM app_template " +
            "WHERE deleted = 1 AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) ORDER BY COALESCE(deleted_time, update_time) DESC")
    List<Map<String, Object>> listDeletedTemplate(@Param("tenantId") Long tenantId);

    @Select("SELECT id, title AS name, deleted_time AS deleted_time FROM sys_announcement " +
            "WHERE deleted = 1 AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) ORDER BY COALESCE(deleted_time, update_time) DESC")
    List<Map<String, Object>> listDeletedAnnouncement(@Param("tenantId") Long tenantId);

    @Select("SELECT id, name AS name, deleted_time AS deleted_time FROM agent_team " +
            "WHERE deleted = 1 AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) ORDER BY COALESCE(deleted_time, update_time) DESC")
    List<Map<String, Object>> listDeletedTeam(@Param("tenantId") Long tenantId);

    @Select("SELECT id, name AS name, deleted_time AS deleted_time FROM app_tool " +
            "WHERE deleted = 1 AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) ORDER BY COALESCE(deleted_time, update_time) DESC")
    List<Map<String, Object>> listDeletedTool(@Param("tenantId") Long tenantId);

    @Select("SELECT id, name AS name, deleted_time AS deleted_time FROM app_skill " +
            "WHERE deleted = 1 AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) ORDER BY COALESCE(deleted_time, update_time) DESC")
    List<Map<String, Object>> listDeletedSkill(@Param("tenantId") Long tenantId);

    @Select("SELECT id, name AS name, deleted_time AS deleted_time FROM app_prompt_template " +
            "WHERE deleted = 1 AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) ORDER BY COALESCE(deleted_time, update_time) DESC")
    List<Map<String, Object>> listDeletedPromptTemplate(@Param("tenantId") Long tenantId);

    @Select("SELECT id, title AS name, deleted_time AS deleted_time FROM chat_conversation " +
            "WHERE deleted = 1 AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) ORDER BY COALESCE(deleted_time, update_time) DESC")
    List<Map<String, Object>> listDeletedConversation(@Param("tenantId") Long tenantId);

    @Select("SELECT id, name AS name, deleted_time AS deleted_time FROM data_table " +
            "WHERE deleted = 1 AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) ORDER BY COALESCE(deleted_time, update_time) DESC")
    List<Map<String, Object>> listDeletedDataTable(@Param("tenantId") Long tenantId);

    /* ---------------- 恢复 ---------------- */

    @Update("UPDATE app_agent SET deleted = 0, deleted_time = NULL WHERE id = #{id}")
    int restoreAgent(@Param("id") Long id);

    @Update("UPDATE knowledge_dataset SET deleted = 0, deleted_time = NULL WHERE id = #{id}")
    int restoreDataset(@Param("id") Long id);

    @Update("UPDATE app_template SET deleted = 0, deleted_time = NULL WHERE id = #{id}")
    int restoreTemplate(@Param("id") Long id);

    @Update("UPDATE sys_announcement SET deleted = 0, deleted_time = NULL WHERE id = #{id}")
    int restoreAnnouncement(@Param("id") Long id);

    @Update("UPDATE agent_team SET deleted = 0, deleted_time = NULL WHERE id = #{id}")
    int restoreTeam(@Param("id") Long id);

    @Update("UPDATE app_tool SET deleted = 0, deleted_time = NULL WHERE id = #{id}")
    int restoreTool(@Param("id") Long id);

    @Update("UPDATE app_skill SET deleted = 0, deleted_time = NULL WHERE id = #{id}")
    int restoreSkill(@Param("id") Long id);

    @Update("UPDATE app_prompt_template SET deleted = 0, deleted_time = NULL WHERE id = #{id}")
    int restorePromptTemplate(@Param("id") Long id);

    /** 会话恢复时需同时把 status 复位为正常（删除时会置 0） */
    @Update("UPDATE chat_conversation SET deleted = 0, deleted_time = NULL, status = 1 WHERE id = #{id}")
    int restoreConversation(@Param("id") Long id);

    @Update("UPDATE data_table SET deleted = 0, deleted_time = NULL WHERE id = #{id}")
    int restoreDataTable(@Param("id") Long id);

    /* ---------------- 彻底删除（级联） ---------------- */

    @Delete("DELETE FROM app_agent WHERE id = #{id}")
    int purgeAgent(@Param("id") Long id);

    @Delete("DELETE FROM app_agent_version WHERE app_id = #{id}")
    int purgeAgentVersions(@Param("id") Long id);

    @Delete("DELETE FROM chat_usage WHERE app_id = #{id}")
    int purgeAgentUsage(@Param("id") Long id);

    @Delete("DELETE FROM chat_message WHERE conversation_id IN " +
            "(SELECT id FROM chat_conversation WHERE app_id = #{id})")
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

    @Delete("DELETE FROM agent_team WHERE id = #{id}")
    int purgeTeam(@Param("id") Long id);

    @Delete("DELETE FROM agent_team_member WHERE team_id = #{id}")
    int purgeTeamMembers(@Param("id") Long id);

    @Delete("DELETE FROM agent_team_run WHERE team_id = #{id}")
    int purgeTeamRuns(@Param("id") Long id);

    @Delete("DELETE FROM app_tool WHERE id = #{id}")
    int purgeTool(@Param("id") Long id);

    @Delete("DELETE FROM app_skill WHERE id = #{id}")
    int purgeSkill(@Param("id") Long id);

    @Delete("DELETE FROM app_skill_version WHERE skill_id = #{id}")
    int purgeSkillVersions(@Param("id") Long id);

    @Delete("DELETE FROM app_prompt_template WHERE id = #{id}")
    int purgePromptTemplate(@Param("id") Long id);

    @Delete("DELETE FROM app_prompt_version WHERE template_id = #{id}")
    int purgePromptVersions(@Param("id") Long id);

    @Delete("DELETE FROM chat_conversation WHERE id = #{id}")
    int purgeConversation(@Param("id") Long id);

    @Delete("DELETE FROM chat_message WHERE conversation_id = #{id}")
    int purgeConversationMessages(@Param("id") Long id);

    @Delete("DELETE FROM chat_usage WHERE conversation_id = #{id}")
    int purgeConversationUsage(@Param("id") Long id);

    @Delete("DELETE FROM data_table WHERE id = #{id}")
    int purgeDataTable(@Param("id") Long id);

    @Delete("DELETE FROM data_record WHERE table_id = #{id}")
    int purgeDataRecords(@Param("id") Long id);

    /* ---------------- 影响预览（级联子数据计数，PG/MySQL 通用） ---------------- */

    @Select("SELECT COUNT(*) FROM app_agent_version WHERE app_id = #{id}")
    long countAgentVersions(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM chat_usage WHERE app_id = #{id}")
    long countAgentUsage(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM chat_message WHERE conversation_id IN " +
            "(SELECT id FROM chat_conversation WHERE app_id = #{id})")
    long countAgentMessages(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM chat_conversation WHERE app_id = #{id}")
    long countAgentConversations(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM knowledge_chunk WHERE dataset_id = #{id}")
    long countDatasetChunks(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM knowledge_document WHERE dataset_id = #{id}")
    long countDatasetDocuments(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM agent_team_member WHERE team_id = #{id}")
    long countTeamMembers(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM agent_team_run WHERE team_id = #{id}")
    long countTeamRuns(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM app_skill_version WHERE skill_id = #{id}")
    long countSkillVersions(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM app_prompt_version WHERE template_id = #{id}")
    long countPromptVersions(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM chat_message WHERE conversation_id = #{id}")
    long countConversationMessages(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM chat_usage WHERE conversation_id = #{id}")
    long countConversationUsage(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM data_record WHERE table_id = #{id}")
    long countDataRecords(@Param("id") Long id);
}
