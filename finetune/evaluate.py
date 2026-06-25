"""
评估情绪/风险分类效果：在验证集上对比【基座 zero-shot】vs【微调后】。
算 emotion 与 riskLevel 的 accuracy + macro-F1 + 混淆矩阵。

前置：prepare_data.py 已生成 data/val_raw.jsonl
用法：
    python evaluate.py --mode base       # 基座 Qwen2.5-3B-Instruct 零样本
    python evaluate.py --mode finetuned  # 加载 out/adapter 的微调模型
"""
import argparse
import json
import re
from pathlib import Path

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig
from sklearn.metrics import accuracy_score, f1_score, confusion_matrix

BASE_MODEL = "Qwen/Qwen2.5-3B-Instruct"
ADAPTER = "out/adapter"
VAL_RAW = Path(__file__).parent / "data" / "val_raw.jsonl"
EMOTIONS = ["NORMAL", "ANXIETY", "DEPRESSED", "HIGH_RISK"]
RISKS = ["LOW", "MEDIUM", "HIGH"]

SYSTEM = (
    "你是校园心理风险评估器。只输出一个 JSON 对象，不要任何多余文字或解释。\n"
    "根据学生这句话，评估心理风险，字段：\n"
    "- riskLevel: LOW / MEDIUM / HIGH\n"
    "- emotion: NORMAL / ANXIETY / DEPRESSED / HIGH_RISK\n"
    "- reason: 不超过30字的中文理由\n"
    "输出示例：{\"riskLevel\":\"LOW\",\"emotion\":\"NORMAL\",\"reason\":\"日常闲聊\"}"
)


def load_model(mode):
    bnb = BitsAndBytesConfig(load_in_4bit=True, bnb_4bit_quant_type="nf4",
                             bnb_4bit_use_double_quant=True,
                             bnb_4bit_compute_dtype=torch.float16)
    tok = AutoTokenizer.from_pretrained(BASE_MODEL)
    model = AutoModelForCausalLM.from_pretrained(
        BASE_MODEL, quantization_config=bnb, device_map="auto", torch_dtype=torch.float16)
    if mode == "finetuned":
        from peft import PeftModel
        model = PeftModel.from_pretrained(model, ADAPTER)
    model.eval()
    return model, tok


def predict(model, tok, text):
    msgs = [{"role": "system", "content": SYSTEM},
            {"role": "user", "content": "学生的话：「" + text + "」"}]
    prompt = tok.apply_chat_template(msgs, tokenize=False, add_generation_prompt=True)
    inputs = tok(prompt, return_tensors="pt").to(model.device)
    with torch.no_grad():
        out = model.generate(**inputs, max_new_tokens=64, do_sample=False,
                             pad_token_id=tok.eos_token_id)
    gen = tok.decode(out[0][inputs["input_ids"].shape[1]:], skip_special_tokens=True)
    m = re.search(r"\{.*\}", gen, re.S)
    if not m:
        return "NORMAL", "LOW"
    try:
        d = json.loads(m.group())
        emo = d.get("emotion", "NORMAL")
        risk = d.get("riskLevel", "LOW")
        return (emo if emo in EMOTIONS else "NORMAL"), (risk if risk in RISKS else "LOW")
    except Exception:
        return "NORMAL", "LOW"


def report(name, labels, preds, classes):
    acc = accuracy_score(labels, preds)
    f1 = f1_score(labels, preds, labels=classes, average="macro", zero_division=0)
    print(f"\n[{name}] accuracy={acc:.3f}  macro-F1={f1:.3f}")
    cm = confusion_matrix(labels, preds, labels=classes)
    print("混淆矩阵(行=真实, 列=预测):", classes)
    for c, row in zip(classes, cm):
        print(f"  {c:<10} {row.tolist()}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", choices=["base", "finetuned"], required=True)
    args = ap.parse_args()

    rows = [json.loads(l) for l in VAL_RAW.read_text(encoding="utf-8").splitlines() if l.strip()]
    model, tok = load_model(args.mode)

    emo_true, emo_pred, risk_true, risk_pred = [], [], [], []
    for i, r in enumerate(rows, 1):
        pe, pr = predict(model, tok, r["text"])
        emo_true.append(r["emotion"]); emo_pred.append(pe)
        risk_true.append(r["riskLevel"]); risk_pred.append(pr)
        if i % 20 == 0:
            print(f"  ...{i}/{len(rows)}")

    print(f"\n===== 模式: {args.mode}  样本: {len(rows)} =====")
    report("emotion", emo_true, emo_pred, EMOTIONS)
    report("riskLevel", risk_true, risk_pred, RISKS)


if __name__ == "__main__":
    main()
