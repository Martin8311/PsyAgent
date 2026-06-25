"""
数据准备：把你标注的 emotion_train.jsonl 转成训练用的 chat 格式，并切分 train/val。

输入(你准备的)：finetune/data/emotion_train.jsonl，每行：
    {"text":"...", "riskLevel":"LOW|MEDIUM|HIGH", "emotion":"NORMAL|ANXIETY|DEPRESSED|HIGH_RISK", "reason":"≤30字"}

输出：
    finetune/data/train.jsonl     训练集(messages 格式, 供 SFTTrainer)
    finetune/data/val.jsonl       验证集(messages 格式)
    finetune/data/val_raw.jsonl   验证集原始标注(供 evaluate.py 算指标)

用法：python prepare_data.py
"""
import json
import random
from collections import Counter
from pathlib import Path

DATA_DIR = Path(__file__).parent / "data"
SRC = DATA_DIR / "emotion_train.jsonl"
VAL_RATIO = 0.12
SEED = 42

RISK_LEVELS = {"LOW", "MEDIUM", "HIGH"}
EMOTIONS = {"NORMAL", "ANXIETY", "DEPRESSED", "HIGH_RISK"}

# 必须与线上 RiskAssessmentService.llmAssess 的 system 完全一致(训练/推理对齐)
SYSTEM = (
    "你是校园心理风险评估器。只输出一个 JSON 对象，不要任何多余文字或解释。\n"
    "根据学生这句话，评估心理风险，字段：\n"
    "- riskLevel: LOW / MEDIUM / HIGH\n"
    "- emotion: NORMAL / ANXIETY / DEPRESSED / HIGH_RISK\n"
    "- reason: 不超过30字的中文理由\n"
    "输出示例：{\"riskLevel\":\"LOW\",\"emotion\":\"NORMAL\",\"reason\":\"日常闲聊\"}"
)


def to_messages(rec):
    """一条标注 → chat 三段：system / user(学生话) / assistant(目标 JSON)。"""
    target = {
        "riskLevel": rec["riskLevel"],
        "emotion": rec["emotion"],
        "reason": rec.get("reason", ""),
    }
    return {"messages": [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": "学生的话：「" + rec["text"].strip() + "」"},
        {"role": "assistant", "content": json.dumps(target, ensure_ascii=False)},
    ]}


def main():
    if not SRC.exists():
        raise SystemExit(f"找不到 {SRC}，请先按格式准备标注数据")

    records, bad = [], 0
    for ln, line in enumerate(SRC.read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if not line:
            continue
        try:
            r = json.loads(line)
            assert r["text"].strip()
            assert r["riskLevel"] in RISK_LEVELS, f"riskLevel 非法: {r.get('riskLevel')}"
            assert r["emotion"] in EMOTIONS, f"emotion 非法: {r.get('emotion')}"
            records.append(r)
        except Exception as e:
            bad += 1
            print(f"[跳过] 第 {ln} 行: {e}")

    if not records:
        raise SystemExit("没有有效样本")

    # 类别分布与基本健康检查
    print(f"\n有效样本 {len(records)} 条，跳过 {bad} 条")
    print("emotion 分布:", dict(Counter(r["emotion"] for r in records)))
    print("riskLevel 分布:", dict(Counter(r["riskLevel"] for r in records)))
    hr = sum(1 for r in records if r["emotion"] == "HIGH_RISK")
    if hr < 100:
        print(f"⚠ HIGH_RISK 仅 {hr} 条，建议 ≥150 条，覆盖直接/隐晦/反讽表达")

    # 按 emotion 分层切分，保证 val 各类都有
    random.seed(SEED)
    by_cls = {}
    for r in records:
        by_cls.setdefault(r["emotion"], []).append(r)
    train, val = [], []
    for cls, items in by_cls.items():
        random.shuffle(items)
        k = max(1, int(len(items) * VAL_RATIO))
        val.extend(items[:k])
        train.extend(items[k:])
    random.shuffle(train)
    random.shuffle(val)

    DATA_DIR.mkdir(exist_ok=True)
    _dump(DATA_DIR / "train.jsonl", (to_messages(r) for r in train))
    _dump(DATA_DIR / "val.jsonl", (to_messages(r) for r in val))
    _dump(DATA_DIR / "val_raw.jsonl", (r for r in val))
    print(f"\n✓ 训练集 {len(train)} 条 → train.jsonl")
    print(f"✓ 验证集 {len(val)} 条 → val.jsonl / val_raw.jsonl")


def _dump(path, rows):
    with path.open("w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    main()
