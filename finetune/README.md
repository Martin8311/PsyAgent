# QLoRA 情绪评分微调

用 QLoRA 微调 `Qwen2.5-3B-Instruct`，提升 MindBridge 的**情绪/风险评估**准确率，
产出独立模型 `mindbridge-emotion` 接回 Ollama，与对话模型 `qwen2.5:3b` 互不影响。

> 微调只增强 [RiskAssessmentService.llmAssess](../src/main/java/com/mindbridge/risk/RiskAssessmentService.java) 这一层；
> **高风险词硬规则保留不动**（不漏报底线）。

---

## 任务定义（训练/推理完全一致）

| | |
|---|---|
| 输入 | 学生一句话 |
| 输出 | `{"riskLevel":"LOW/MEDIUM/HIGH","emotion":"NORMAL/ANXIETY/DEPRESSED/HIGH_RISK","reason":"≤30字"}` |

`system` 提示固化在 `prepare_data.py` / `evaluate.py`，与线上 Java 端逐字一致。

---

## 你要准备的数据

`finetune/data/emotion_train.jsonl`，每行一条：

```json
{"text":"明天期末考，我一晚没睡，心跳很快", "riskLevel":"MEDIUM", "emotion":"ANXIETY", "reason":"考前失眠心慌"}
```

标注规范、类别定义、数量与平衡建议见项目对话里的设计说明。要点回顾：
- 总量 **800~1500+**，每个情绪类 **≥200**，`NORMAL` ≤35%，`HIGH_RISK` ≥150 且覆盖隐晦表达。
- `emotion` 与 `riskLevel` 别标矛盾（HIGH_RISK≈HIGH，NORMAL≈LOW）。

---

## 环境（本地 GPU 8~10GB）

```bash
# 建议 Python 3.10+，先按你的 CUDA 版本装 torch（https://pytorch.org）
pip install -r requirements.txt
# Windows 若 bitsandbytes 报错：pip install -U bitsandbytes（需 >=0.43），或用 WSL2
nvidia-smi   # 确认显存
```

QLoRA 4bit 下 3B 训练约需 **8~10GB**。8GB 偏紧：把 `train_qlora.py` 的 `MAX_SEQ_LEN` 降到 256、`GRAD_ACCUM` 提到 32。

---

## 流程

```bash
# 1) 数据转换 + 切分（生成 train.jsonl / val.jsonl / val_raw.jsonl）
python prepare_data.py

# 2) 微调前基线（基座零样本，记下分数做对比）
python evaluate.py --mode base

# 3) QLoRA 训练（单卡约 1~3 小时，取决于数据量）
python train_qlora.py            # → out/adapter/

# 4) 微调后评估（对比 base 的 accuracy / macro-F1 / 混淆矩阵）
python evaluate.py --mode finetuned

# 5) 合并 adapter 到基座 → 完整 HF 模型
python merge_and_export.py       # 显存不足加 --cpu  → out/merged/
```

### 6) 转 GGUF 并导入 Ollama

```bash
# 需要 llama.cpp（git clone https://github.com/ggerganov/llama.cpp）
python llama.cpp/convert_hf_to_gguf.py out/merged --outfile mindbridge-emotion-f16.gguf
# 量化到 q4_k_m（体积小、够用）
./llama.cpp/llama-quantize mindbridge-emotion-f16.gguf mindbridge-emotion-q4_k_m.gguf q4_k_m
# 导入 Ollama（Modelfile 里的 FROM 指向上面的 gguf）
ollama create mindbridge-emotion -f Modelfile
ollama list   # 应能看到 mindbridge-emotion
```

### 7) 接回 MindBridge（改 1 行配置）

`src/main/resources/application.yml`：

```yaml
mindbridge:
  ai:
    ollama:
      emotion-model: "mindbridge-emotion"   # 原来是 ""(回退到 qwen2.5:3b)
```

重启应用即可——`RiskAssessmentService` 会自动走微调模型，对话仍用 `qwen2.5:3b`。
后台「Token 用量」里 `RISK_ASSESS` 这条的 model 会显示成 `mindbridge-emotion`，可据此确认已生效。

---

## 产物目录（已 gitignore，不入库）

```
finetune/
  data/        emotion_train.jsonl(你的) + 脚本生成的 train/val
  out/         adapter / merged / gguf —— 训练产物，体积大，不提交
```
