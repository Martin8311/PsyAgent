package com.mindbridge.agent;

/**
 * 用户输入的意图分类，由 SupervisorAgent 判定，决定后续 Agent 分支路由。
 */
public enum Intent {

    /** 普通闲聊 / 陪伴。 */
    CHAT,

    /** 心理咨询：需要知识库 + 专业咨询回复。 */
    CONSULT,

    /** 高风险场景：自伤/自杀等倾向，需重点评估与预警。 */
    RISK
}
