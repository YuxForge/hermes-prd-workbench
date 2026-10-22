-- V015: Major version update log for Online Requirement Review & Workspace Overhaul
UPDATE prd_update_log SET is_latest = 0 WHERE is_latest = 1;

INSERT INTO prd_update_log (
    id, version, release_date, title, summary,
    highlights_json, items_json, is_latest, status, created_by, created_at, updated_at
) VALUES (
    'log-v050-beta1',
    'V0.5.0-beta.1',
    '2026-10-22',
    '线上需求评审协同与体验重构重要更新',
    '重大功能发布：全面上线独立“线上评审”协同工作台与快捷入口，支持4级成员角色权限、正文划词精准行号定位、全量 Markdown 富文本渲染与一键极简解决闭环；人员权限新增用户账号与名称模糊搜索联想。',
    '["线上评审独立工作台", "精准划词行号定位", "全量Markdown富文本渲染", "一键确认极简闭环", "人员权限模糊搜索下拉"]',
    '[{"title": "线上评审独立入口与协同工作台", "detail": "顶栏与工作台均提供独立直观的“线上评审”中心与任务快捷入口，支持4级团队权限、多轮次审查基线比对与审计归档导出。", "type": "feature", "tone": "accent"}, {"title": "划词选区与正文行号精准映射", "detail": "重构选区行号计算算法，智能剔除 Markdown 语法标记，基于选区首尾语义片段及上下文精确定位正文行号，支持实时富文本片段预览。", "type": "improve", "tone": "blue"}, {"title": "全量 Markdown 富文本排版渲染", "detail": "提问片段预览、抽屉批注引用、问题描述正文与交流答复记录全面接入 Markdown 渲染，支持标题、列表、粗体与深色代码块语法卡片。", "type": "improve", "tone": "accent"}, {"title": "一键极简解决闭环与催办机制", "detail": "彻底移除繁琐的解决申请弹窗；责任人回复自动推进至待确认，提出人一键即可直接确认解决闭环；未答复问题支持一键催办并记录时间线。", "type": "improve", "tone": "amber"}, {"title": "人员权限账号/名称模糊搜索加人", "detail": "项目成员与权限分配弹窗新增支持用户账号、名称的模糊搜索与实时下拉联想选择，彻底告别手动输入用户ID。", "type": "feature", "tone": "blue"}]',
    1,
    'PUBLISHED',
    'system',
    NOW(3),
    NOW(3)
)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    summary = VALUES(summary),
    highlights_json = VALUES(highlights_json),
    items_json = VALUES(items_json),
    is_latest = VALUES(is_latest),
    status = VALUES(status),
    updated_at = NOW(3);
