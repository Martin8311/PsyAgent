package com.mindbridge.risk;

/**
 * 高危预警处置状态（两段式邮件的闸门）。
 *
 * <p>PENDING_REVIEW：命中高危后自动发管理员，等待人工核实；
 * GUARDIAN_NOTIFIED：管理员核实后已一键通知监护人；
 * DISMISSED：管理员判定为误报，已忽略，不通知监护人。
 */
public enum AlertStatus {
    PENDING_REVIEW,
    GUARDIAN_NOTIFIED,
    DISMISSED
}
