"""
QLoRA 微调 Qwen2.5-3B-Instruct 做情绪/风险评估。针对 8~10GB 显存调优。

前置：python prepare_data.py 已生成 data/train.jsonl
用法：python train_qlora.py
产出：out/adapter/  (LoRA adapter)

显存吃紧(8GB)时的进一步手段：把 MAX_SEQ_LEN 降到 256、BATCH 保持 1、
GRAD_ACCUM 提到 32；或关掉部分 target_modules。
"""
import torch
from datasets import load_dataset
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig
from peft import LoraConfig, prepare_model_for_kbit_training
from trl import SFTConfig, SFTTrainer

BASE_MODEL = "Qwen/Qwen2.5-3B-Instruct"
TRAIN_FILE = "data/train.jsonl"
OUT_DIR = "out"
MAX_SEQ_LEN = 384          # 情绪评估输入短，384 足够且省显存
EPOCHS = 3
LR = 2e-4
BATCH = 1
GRAD_ACCUM = 16            # 等效 batch=16

# bf16 在 Ampere(30 系)+ 支持；老卡(20 系)改用 fp16
USE_BF16 = torch.cuda.is_bf16_supported()
COMPUTE_DTYPE = torch.bfloat16 if USE_BF16 else torch.float16


def main():
    bnb = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_use_double_quant=True,
        bnb_4bit_compute_dtype=COMPUTE_DTYPE,
    )
    tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL)
    model = AutoModelForCausalLM.from_pretrained(
        BASE_MODEL, quantization_config=bnb, device_map="auto", torch_dtype=COMPUTE_DTYPE
    )
    model = prepare_model_for_kbit_training(model, use_gradient_checkpointing=True)
    model.config.use_cache = False

    lora = LoraConfig(
        r=16, lora_alpha=32, lora_dropout=0.05, bias="none", task_type="CAUSAL_LM",
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj",
                        "gate_proj", "up_proj", "down_proj"],
    )

    train_ds = load_dataset("json", data_files=TRAIN_FILE, split="train")

    args = SFTConfig(
        output_dir=OUT_DIR,
        per_device_train_batch_size=BATCH,
        gradient_accumulation_steps=GRAD_ACCUM,
        num_train_epochs=EPOCHS,
        learning_rate=LR,
        lr_scheduler_type="cosine",
        warmup_ratio=0.03,
        logging_steps=10,
        save_strategy="epoch",
        bf16=USE_BF16,
        fp16=not USE_BF16,
        gradient_checkpointing=True,
        gradient_checkpointing_kwargs={"use_reentrant": False},
        max_seq_length=MAX_SEQ_LEN,
        optim="paged_adamw_8bit",
        report_to="none",
        packing=False,
    )

    trainer = SFTTrainer(
        model=model,
        args=args,
        train_dataset=train_ds,
        peft_config=lora,
        processing_class=tokenizer,   # 旧版 trl 用 tokenizer=tokenizer
    )
    trainer.train()
    trainer.save_model(f"{OUT_DIR}/adapter")
    tokenizer.save_pretrained(f"{OUT_DIR}/adapter")
    print(f"\n✓ LoRA adapter 已保存到 {OUT_DIR}/adapter")


if __name__ == "__main__":
    main()
