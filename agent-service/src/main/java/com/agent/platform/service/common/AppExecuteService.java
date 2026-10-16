package com.agent.platform.service.common;

/**
 * 应用/模型程序化执行服务（供渠道回调、评测运行、多智能体路由等内部场景复用）。
 *
 * <p>与 {@code PortalPublicController.chat} 语义一致：
 * workflow → 运行已发布版本 DSL；agent → 自主工具调用；chatflow → 默认模型直连。</p>
 */
public interface AppExecuteService {

    /** 执行结果 */
    public record Reply(String answer, Object detail) {
    }

    /**
     * 运行已发布应用（workflow/agent/chatflow）。
     *
     * @param appId   应用ID（须为已发布 status=1）
     * @param content 用户输入
     */
    public Reply runApp(Long appId, String content);

    /** 直连模型执行（评测“模型模式”使用） */
    public Reply runModel(Long modelId, String content);
}
